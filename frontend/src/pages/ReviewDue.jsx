import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { answerProgress, fetchProgressSummary, fetchReviewQueue, recordSession, resolveWrong } from '../api/study.js';
import { getErrorMessage } from '../api/client.js';
import AudioButton from '../components/AudioButton.jsx';
import Confetti from '../components/Confetti.jsx';
import { CheckIcon, CrossIcon, FlameIcon, PencilIcon } from '../components/icons.jsx';
import './pages.css';

// F-043 (FR-043): 1 hàng đợi ôn tập duy nhất trộn 3 loại item —
// wrong (câu sai: từ quiz/flashcard + câu Sensei chấm < 60đ) xếp trước,
// rồi vocab/kanji đến hạn SM-2 theo overdueDays giảm dần.
// Flashcard → PATCH /api/progress/answer (kind vocab|kanji); trả lời đúng
// item wrong → PATCH /api/review/wrong/{id} resolve. Hết queue → POST sessions
// mode REVIEW_DUE (giữ streak/badge như cũ).

function normalizeJa(s) {
  return (s || '').toLowerCase().replace(/[\s\u3000\p{P}]/gu, '');
}

const KIND_META = {
  wrong: { badge: '誤', cls: 'shu', label: 'Câu sai cần sửa' },
  vocab: { badge: '語', cls: 'matcha', label: 'Từ vựng đến hạn' },
  kanji: { badge: '漢', cls: 'gold', label: 'Kanji đến hạn' },
};

export default function ReviewDue() {
  const [queue, setQueue] = useState(null);
  const [streak, setStreak] = useState(null);
  const [idx, setIdx] = useState(0);
  const [flipped, setFlipped] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [sessionItems, setSessionItems] = useState([]);
  const [finished, setFinished] = useState(false);
  const [result, setResult] = useState(null);
  const [saving, setSaving] = useState(false);
  const [cardStart, setCardStart] = useState(Date.now());
  const [aiInput, setAiInput] = useState('');
  const [aiFeedback, setAiFeedback] = useState('');
  const startedAt = useMemo(() => new Date().toISOString(), []);

  useEffect(() => {
    setLoading(true);
    Promise.all([fetchReviewQueue(50), fetchProgressSummary().catch(() => null)])
      .then(([q, summary]) => {
        setQueue(q && Array.isArray(q.items) ? q : { items: [], counts: { vocab: 0, kanji: 0, wrong: 0, total: 0 }, estimatedMinutes: 0 });
        setStreak(summary ? summary.streakDays : null);
        setCardStart(Date.now());
      })
      .catch((e) => setError(getErrorMessage(e, 'Không tải được hàng đợi ôn tập.')))
      .finally(() => setLoading(false));
  }, []);

  const items = queue ? queue.items : [];

  const advance = (newSessionItems) => {
    setAiInput('');
    setAiFeedback('');
    setSaving(false); // SHOULD-FIX #4: mo khoa nut sau moi lan chuyen the
    if (idx + 1 >= items.length) {
      finishSession(newSessionItems);
    } else {
      setIdx(idx + 1);
      setFlipped(false);
      setCardStart(Date.now());
    }
  };

  const finishSession = async (newSessionItems) => {
    setSaving(true);
    try {
      const correctCount = newSessionItems.filter((i) => i.correct).length;
      const res = await recordSession({
        mode: 'REVIEW_DUE',
        scopeType: 'REVIEW',
        scopeId: 'due',
        totalItems: newSessionItems.length,
        correctCount,
        items: newSessionItems,
        startedAt,
        finishedAt: new Date().toISOString(),
      });
      setResult({ correctCount, total: newSessionItems.length, streakDays: res.streakDays });
    } catch {
      const correctCount = newSessionItems.filter((i) => i.correct).length;
      setResult({ correctCount, total: newSessionItems.length, streakDays: null });
    } finally {
      setSaving(false);
      setFinished(true);
    }
  };

  // Trả lời flashcard (vocab / kanji / wrong+refKind=vocab).
  const answerFlashcard = async (correct) => {
    const item = items[idx];
    const timeMs = Date.now() - cardStart;
    const isKanji = item.kind === 'kanji';
    const wordId = isKanji ? item.character : item.wordId;
    // SHOULD-FIX #5 (code review): await progress TRUOC khi resolve — neu PATCH
    // progress loi mang ma van resolve thi cau sai bien mat khoi queue ma SM-2
    // khong duoc cap nhat.
    try {
      await answerProgress({
        kind: isKanji ? 'kanji' : 'vocab',
        wordId: isKanji ? undefined : wordId,
        character: isKanji ? item.character : undefined,
        correct,
        timeMs,
      });
    } catch {
      // Ghi progress lỗi: vẫn cho ôn tiếp, queue lần sau hiện lại.
    }
    // Item wrong trả lời ĐÚNG → resolve câu sai (sau khi ghi progress).
    if (item.kind === 'wrong' && correct && item.id) {
      try {
        await resolveWrong(item.id);
      } catch {
        // Resolve lỗi thì lần sau queue vẫn hiện lại — không chặn luồng ôn.
      }
    }
    const newSessionItems = [...sessionItems, { wordId, correct, timeMs }];
    setSessionItems(newSessionItems);
    advance(newSessionItems);
  };

  // Thẻ câu sai Sensei: bấm "Tôi đã hiểu" → resolve.
  const resolveAiItem = async () => {
    const item = items[idx];
    setSaving(true);
    try {
      await resolveWrong(item.id);
    } catch {
      // như trên: lỗi thì queue lần sau vẫn hiện lại.
    } finally {
      setSaving(false);
    }
    const newSessionItems = [...sessionItems, { wordId: `ai:${item.id}`, correct: true, timeMs: Date.now() - cardStart }];
    setSessionItems(newSessionItems);
    advance(newSessionItems);
  };

  // Thẻ câu sai Sensei: viết lại câu → so khớp đơn giản với câu đã sửa (normalize).
  const checkAiRewrite = async () => {
    const item = items[idx];
    if (!aiInput.trim()) {
      setAiFeedback('Bạn hãy viết lại câu đã sửa rồi bấm kiểm tra nhé.');
      return;
    }
    if (normalizeJa(aiInput) === normalizeJa(item.corrected)) {
      setAiFeedback('Chính xác! Tanu-chan đã đánh dấu bạn sửa được lỗi này.');
      const newSessionItems = [...sessionItems, { wordId: `ai:${item.id}`, correct: true, timeMs: Date.now() - cardStart }];
      setSessionItems(newSessionItems);
      try {
        await resolveWrong(item.id);
      } catch {
        // bỏ qua như trên
      }
      // SHOULD-FIX #4: khoa nut ngay de double-click khong goi finishSession 2 lan.
      setSaving(true);
      setTimeout(() => advance(newSessionItems), 900);
    } else {
      setAiFeedback('Chưa khớp với câu sửa của Sensei — xem lại phần lỗi rồi thử lại, hoặc bấm “Tôi đã hiểu”.');
    }
  };

  if (loading) return <div className="page"><p className="muted">Đang tải hàng đợi ôn tập…</p></div>;
  if (error) return <div className="page"><div className="alert alert-error">{error}</div></div>;

  if (finished && result) {
    return (
      <div className="page result-page">
        <Confetti run />
        <h1>Ôn tập xong!</h1>
        <div className="result-summary">
          <div className="result-big">{result.correctCount}/{result.total}</div>
          <div className="muted">lượt ôn tập bạn còn nhớ</div>
          {result.streakDays != null && (
            <div className="streak-line"><FlameIcon size={16} /> Chuỗi ngày học: {result.streakDays} ngày</div>
          )}
        </div>
        <div className="result-actions">
          <Link to="/hoc" className="btn btn-primary">Học bài mới</Link>
          <Link to="/ca-nhan" className="btn btn-outline">Xem tiến trình</Link>
        </div>
      </div>
    );
  }

  if (items.length === 0) {
    return (
      <div className="page">
        <h1>Ôn tập đến hạn</h1>
        <div className="empty-state">
          <div className="empty-icon"><span className="jp-badge matcha">復</span></div>
          <p>Không có gì đến hạn hôm nay. Tanu-chan thấy bạn đang theo kịp tiến độ — tuyệt vời!</p>
          <Link to="/hoc" className="btn btn-primary">Học từ mới</Link>
        </div>
      </div>
    );
  }

  const item = items[idx];
  const counts = queue.counts || { vocab: 0, kanji: 0, wrong: 0, total: 0 };
  const meta = KIND_META[item.kind] || KIND_META.vocab;
  const remainingSecs = items.slice(idx).reduce((s, it) => s + (it.kind === 'wrong' ? 15 : 8), 0);
  const etaMin = Math.max(1, Math.round(remainingSecs / 60));
  const isAiSentence = item.kind === 'wrong' && item.refKind === 'ai_sentence';
  const isKanji = item.kind === 'kanji';

  return (
    <div className="page practice-page">
      <div className="practice-top">
        <span className="muted">
          Ôn tập {idx + 1}/{items.length} · còn khoảng {etaMin} phút
          <br />
          {counts.total} lượt ôn tập: {counts.wrong} câu sai · {counts.vocab} từ vựng · {counts.kanji} kanji
          {streak != null && (
            <> · <FlameIcon size={13} /> {streak} ngày</>
          )}
        </span>
        <div className="progress-bar slim"><div className="progress-fill" style={{ width: `${(idx / items.length) * 100}%` }} /></div>
      </div>

      <div className="review-kind-row">
        <span className={`jp-badge ${meta.cls}`}>{meta.badge}</span>
        <span className="muted">{meta.label}{item.kind === 'wrong' && item.refKind === 'ai_sentence' ? ' (do Sensei chấm)' : ''}</span>
      </div>

      {isAiSentence ? (
        <div className="ai-review-card">
          <div className="ai-review-block">
            <div className="ai-review-label">Câu của bạn</div>
            <div className="ai-sentence">{item.sentence}</div>
          </div>
          <div className="ai-review-block">
            <div className="ai-review-label">Sensei sửa thành</div>
            <div className="ai-sentence corrected">{item.corrected}</div>
          </div>
          {item.errors && item.errors.length > 0 && (
            <ul className="ai-errors">
              {item.errors.map((e, i) => (
                <li key={i}><strong>{e.span}</strong> — {e.issue}<br /><span className="muted">Sửa: {e.fix}</span></li>
              ))}
            </ul>
          )}
          <div className="rewrite-row">
            <input
              className="rewrite-input"
              value={aiInput}
              onChange={(e) => setAiInput(e.target.value)}
              placeholder="Viết lại câu đã sửa…"
              disabled={saving}
            />
            <button type="button" className="btn btn-outline" onClick={checkAiRewrite} disabled={saving}>
              <PencilIcon size={15} /> Kiểm tra
            </button>
          </div>
          {aiFeedback && <p className="ai-feedback">{aiFeedback}</p>}
          <div className="grade-row">
            <button type="button" className="gbtn yes" onClick={resolveAiItem} disabled={saving}>
              <CheckIcon size={18} /> Tôi đã hiểu, đánh dấu xong
            </button>
          </div>
        </div>
      ) : (
        <>
          {/* Lật thẻ 3D — .fc-scene(perspective) > .fc-3d(preserve-3d) > .fc-face(front/back). */}
          <div className="fc-scene">
            <div className={`fc-3d${flipped ? ' fc-flipped' : ''}`} onClick={() => setFlipped(!flipped)} role="button" aria-label="Lật thẻ">
              <div className="fc-face fc-front">
                {isKanji ? (
                  <div className="flash-ja flash-kanji-big">{item.character}</div>
                ) : (
                  <>
                    <div className="flash-ja">{item.kanji || item.kana}</div>
                    {item.kanji && <div className="flash-kana">{item.kana}</div>}
                    <div className="flash-romaji">{item.romaji}</div>
                    <AudioButton url={item.audioUrl} />
                  </>
                )}
                <div className="flash-hint">Chạm để lật thẻ</div>
              </div>
              <div className="fc-face fc-back">
                <div className="flash-meaning">{item.meaningVi}</div>
                {isKanji && (
                  <div className="flash-yomi">
                    {(item.onYomi && item.onYomi.length > 0) && <div><span className="muted">On: </span>{item.onYomi.join(' · ')}</div>}
                    {(item.kunYomi && item.kunYomi.length > 0) && <div><span className="muted">Kun: </span>{item.kunYomi.join(' · ')}</div>}
                  </div>
                )}
              </div>
            </div>
          </div>
          <div className="grade-row">
            <button type="button" className="gbtn no" onClick={() => answerFlashcard(false)} disabled={saving}>
              <CrossIcon size={18} /> Quên rồi
            </button>
            <button type="button" className="gbtn yes" onClick={() => answerFlashcard(true)} disabled={saving}>
              <CheckIcon size={18} /> Còn nhớ
            </button>
          </div>
        </>
      )}
    </div>
  );
}
