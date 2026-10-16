import { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { answerProgress, generateQuiz, recordSession } from '../api/study.js';
import { generateQuickQuiz } from '../api/quickPractice.js';
import { getErrorMessage } from '../api/client.js';
import { playAudio, resolveAssetUrl, QUIZ_MODE_LABELS } from '../utils.js';
import './pages.css';

// US-006 (F-021/022/023, F-027): quiz 20 câu, chấm tại chỗ, PATCH progress mỗi câu.
// mode listening: phát audio thay vì hiện chữ, preload câu kế (NFR-001).
// F-C: chế độ luyện nhanh (?quick=1) — nhận wordIds/mode từ sessionStorage
// (trang /luyen-tap-nhanh lưu trước khi chuyển sang), gọi POST /api/quiz/quick,
// ghi session với scopeType 'QUICK' / scopeId 'quick', kết quả có 3 nút
// (luyện lại từ sai / luyện lại toàn bộ / chọn lại từ). KHÔNG tạo bộ thẻ.
const MODE_MAP = { jp_vi: 'JP_VI', vi_jp: 'VI_JP', listening: 'LISTENING' };
const SESSION_MODE = { JP_VI: 'QUIZ_JP_VI', VI_JP: 'QUIZ_VI_JP', LISTENING: 'LISTENING' };
const QUICK_KEY = 'mjp_quick_quiz';

export default function Quiz() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const isQuick = params.get('quick') === '1';

  const urlMode = MODE_MAP[params.get('mode')] || 'JP_VI';
  const scopeType = isQuick ? 'QUICK' : params.get('scopeType');
  const scopeId = isQuick ? 'quick' : params.get('scopeId');

  // F-C: đọc payload luyện nhanh (do QuickPractice lưu vào sessionStorage).
  const [quick, setQuick] = useState(() => {
    if (!isQuick) return null;
    try {
      const p = JSON.parse(sessionStorage.getItem(QUICK_KEY) || 'null');
      if (p?.wordIds?.length && p?.mode) {
        return { allWordIds: p.wordIds, wordIds: p.wordIds, mode: p.mode };
      }
    } catch { /* bỏ qua */ }
    return { error: true };
  });
  const [runKey, setRunKey] = useState(0);

  const mode = isQuick ? (quick?.mode || 'JP_VI') : urlMode;
  const wordIds = isQuick ? quick?.wordIds : null;

  const [questions, setQuestions] = useState([]);
  const [idx, setIdx] = useState(0);
  const [selectedId, setSelectedId] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [items, setItems] = useState([]);
  const [finished, setFinished] = useState(false);
  const [result, setResult] = useState(null);
  const [saving, setSaving] = useState(false);
  const [qStart, setQStart] = useState(Date.now());
  const [startedAt, setStartedAt] = useState(() => new Date().toISOString());
  const audioCache = useRef({});

  useEffect(() => {
    if (isQuick && (!wordIds || wordIds.length === 0)) {
      setLoading(false);
      setError('Không tìm thấy danh sách từ để luyện nhanh. Hãy chọn lại từ trang Luyện tập nhanh.');
      return;
    }
    setLoading(true);
    setError('');
    const promise = isQuick
      ? generateQuickQuiz({ wordIds, mode })
      : generateQuiz({ mode, scopeType, scopeId, limit: 20 });
    promise
      .then((data) => {
        const qs = data.questions || [];
        if (qs.length < 4) throw new Error('Phạm vi này không đủ từ để tạo quiz (cần ít nhất 4 từ). Hãy mở rộng phạm vi nhé.');
        setQuestions(qs);
        setQStart(Date.now());
      })
      .catch((e) => setError(getErrorMessage(e, 'Không tạo được bộ câu hỏi.')))
      .finally(() => setLoading(false));
  }, [mode, scopeType, scopeId, isQuick, wordIds, runKey]);

  // Phát audio câu hiện tại; preload câu kế (NFR-001).
  useEffect(() => {
    if (mode !== 'LISTENING' || questions.length === 0 || finished) return;
    const cur = questions[idx];
    if (cur?.promptAudioUrl) {
      const url = resolveAssetUrl(cur.promptAudioUrl);
      if (!audioCache.current[url]) audioCache.current[url] = new Audio(url);
      audioCache.current[url].play().catch(() => {});
    }
    const next = questions[idx + 1];
    if (next?.promptAudioUrl) {
      const nextUrl = resolveAssetUrl(next.promptAudioUrl);
      if (!audioCache.current[nextUrl]) {
        audioCache.current[nextUrl] = new Audio(nextUrl);
        audioCache.current[nextUrl].load();
      }
    }
  }, [idx, mode, questions, finished]);

  const choose = async (wordId) => {
    if (selectedId !== null) return;
    const q = questions[idx];
    const correct = wordId === q.answerWordId;
    const timeMs = Date.now() - qStart;
    setSelectedId(wordId);
    answerProgress({ wordId: q.wordId, correct, timeMs }).catch(() => {});
    const newItems = [...items, { wordId: q.wordId, correct, timeMs }];
    setItems(newItems);
    setTimeout(() => {
      if (idx + 1 >= questions.length) {
        finish(newItems, questions);
      } else {
        setIdx(idx + 1);
        setSelectedId(null);
        setQStart(Date.now());
      }
    }, 900);
  };

  const finish = async (allItems, qs) => {
    setSaving(true);
    const correctCount = allItems.filter((i) => i.correct).length;
    const wrong = qs.filter((_, i) => !allItems[i]?.correct);
    try {
      const res = await recordSession({
        mode: SESSION_MODE[mode],
        scopeType,
        scopeId,
        totalItems: allItems.length,
        correctCount,
        items: allItems,
        startedAt,
        finishedAt: new Date().toISOString(),
      });
      setResult({ correctCount, total: allItems.length, streakDays: res.streakDays, wrong, items: allItems });
    } catch {
      setResult({ correctCount, total: allItems.length, streakDays: null, wrong, items: allItems });
    } finally {
      setSaving(false);
      setFinished(true);
    }
  };

  // F-C: chạy lại quiz với tập từ mới (từ sai hoặc toàn bộ), ở nguyên trang.
  const restartQuick = (newWordIds) => {
    setQuick((prev) => ({ ...prev, wordIds: newWordIds }));
    setQuestions([]);
    setIdx(0);
    setSelectedId(null);
    setItems([]);
    setFinished(false);
    setResult(null);
    setSaving(false);
    setError('');
    setStartedAt(new Date().toISOString());
    audioCache.current = {};
    setRunKey((k) => k + 1);
  };

  if (loading) return <div className="page"><p className="muted">Đang tạo bộ câu hỏi…</p></div>;
  if (error) return (
    <div className="page">
      <div className="alert alert-error">{error}</div>
      <button type="button" className="btn btn-outline" onClick={() => navigate(isQuick ? '/luyen-tap-nhanh' : -1)}>
        ← Quay lại
      </button>
    </div>
  );

  if (finished && result) {
    const pctScore = Math.round((result.correctCount / result.total) * 100);
    const wrongIds = result.wrong.map((q) => q.wordId);
    return (
      <div className="page result-page">
        <h1>Kết quả Quiz</h1>
        <div className="result-summary">
          <div className="result-big">{result.correctCount}/{result.total} <span className="result-pct">({pctScore}%)</span></div>
          <div className="muted">Dạng: {QUIZ_MODE_LABELS[mode]}</div>
          {result.streakDays != null && <div className="streak-line">🔥 Chuỗi ngày học: {result.streakDays} ngày</div>}
        </div>
        {result.wrong.length > 0 && (
          <>
            <h3>Câu sai cần ôn lại ({result.wrong.length})</h3>
            <div className="word-list">
              {result.wrong.map((q) => {
                const right = q.options.find((o) => o.wordId === q.answerWordId);
                return (
                  <div key={q.wordId} className="quiz-wrong-item">
                    <div>
                      <strong>{q.promptText || '🎧 Nghe'}</strong>
                      <span className="quiz-wrong-answer"> → Đáp án đúng: {right?.text}</span>
                    </div>
                    {q.promptAudioUrl && (
                      <button type="button" className="btn btn-sm btn-outline" onClick={() => playAudio(q.promptAudioUrl)}>🔊 Nghe lại</button>
                    )}
                  </div>
                );
              })}
            </div>
          </>
        )}
        {isQuick ? (
          <div className="result-actions">
            {/* Quiz cần ít nhất 4 từ để tạo đáp án nhiễu — ẩn nút thay vì rơi vào trang lỗi. */}
            {wrongIds.length >= 4 ? (
              <button type="button" className="btn btn-primary" onClick={() => restartQuick(wrongIds)}>
                🔁 Luyện lại từ sai ({wrongIds.length})
              </button>
            ) : wrongIds.length > 0 ? (
              <span className="muted">Còn {wrongIds.length} từ sai — cần ít nhất 4 từ để tạo quiz mới, hãy luyện lại toàn bộ.</span>
            ) : null}
            <button type="button" className="btn btn-outline" onClick={() => restartQuick(quick.allWordIds)}>
              🔁 Luyện lại toàn bộ
            </button>
            <button type="button" className="btn btn-outline" onClick={() => navigate('/luyen-tap-nhanh')}>
              ✏️ Chọn lại từ
            </button>
          </div>
        ) : (
          <div className="result-actions">
            <a href="/on-tap" className="btn btn-outline">Ôn tập đến hạn</a>
            <a href="/bai-hoc" className="btn btn-primary">Học bài khác</a>
          </div>
        )}
      </div>
    );
  }

  const q = questions[idx];
  if (!q) return null;

  return (
    <div className="page practice-page">
      <div className="practice-top">
        <span className="muted">Câu {idx + 1}/{questions.length} · {QUIZ_MODE_LABELS[mode]}{isQuick && ' · Luyện nhanh'}</span>
        <div className="progress-bar slim"><div className="progress-fill" style={{ width: `${(idx / questions.length) * 100}%` }} /></div>
      </div>

      <div className="quiz-prompt">
        {mode === 'LISTENING' ? (
          <div className="quiz-listen">
            <button type="button" className="listen-btn" onClick={() => playAudio(q.promptAudioUrl)} aria-label="Nghe lại">
              🔊
            </button>
            <p className="muted">Nghe và chọn từ đúng</p>
          </div>
        ) : (
          <div className="quiz-text">{q.promptText}</div>
        )}
      </div>

      <div className="quiz-options">
        {q.options.map((opt) => {
          let cls = 'quiz-option';
          if (selectedId !== null) {
            if (opt.wordId === q.answerWordId) cls += ' correct';
            else if (opt.wordId === selectedId) cls += ' wrong';
            else cls += ' dimmed';
          }
          return (
            <button key={opt.wordId} type="button" className={cls} onClick={() => choose(opt.wordId)} disabled={selectedId !== null || saving}>
              {opt.text}
            </button>
          );
        })}
      </div>
    </div>
  );
}
