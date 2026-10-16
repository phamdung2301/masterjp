import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { fetchLessonWords } from '../api/lessons.js';
import { fetchSet } from '../api/sets.js';
import { answerProgress, recordSession } from '../api/study.js';
import { getErrorMessage } from '../api/client.js';
import AudioButton from '../components/AudioButton.jsx';
import { SECTION_LABELS, shuffle } from '../utils.js';
import './pages.css';

// US-005 (F-020, F-027): flashcard — lật thẻ, Nhớ/Chưa nhớ.
// PATCH /api/progress/answer NGAY sau mỗi thẻ (không dồn cuối phiên).
// Hết thẻ → POST /api/sessions → màn kết quả.
// R2: chế độ luyện tập đã chọn (?quick=1) — mirror pattern Quiz.jsx: lấy danh
// sách full word objects từ sessionStorage (trang chi tiết bài lưu trước khi
// chuyển sang, key chung 'mjp_quick_quiz'), KHÔNG gọi API.
const QUICK_KEY = 'mjp_quick_quiz';

export default function Flashcard() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const isQuick = params.get('quick') === '1';
  const scopeType = isQuick ? 'QUICK' : params.get('scopeType');
  const scopeId = isQuick ? 'quick' : params.get('scopeId');
  // Hướng thẻ: jp_vi (mặc định) = mặt trước tiếng Nhật → đoán nghĩa;
  // vi_jp = mặt trước nghĩa Việt → đoán tiếng Nhật.
  const direction = params.get('direction') === 'vi_jp' ? 'vi_jp' : 'jp_vi';

  const [words, setWords] = useState([]);
  const [idx, setIdx] = useState(0);
  const [flipped, setFlipped] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [items, setItems] = useState([]);
  const [finished, setFinished] = useState(false);
  const [result, setResult] = useState(null);
  const [saving, setSaving] = useState(false);
  const [scopeName, setScopeName] = useState('');
  const [returnTo, setReturnTo] = useState(null);
  const [cardStart, setCardStart] = useState(Date.now());
  const startedAt = useMemo(() => new Date().toISOString(), []);

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      setError('');
      try {
        if (isQuick) {
          // R2: lấy full word objects đã lưu (không gọi API).
          let p = null;
          try { p = JSON.parse(sessionStorage.getItem(QUICK_KEY) || 'null'); } catch { /* bỏ qua */ }
          if (p?.words?.length) {
            setWords(shuffle(p.words));
            setScopeName(`${p.words.length} từ đã chọn`);
            setReturnTo(p.returnTo || null);
          } else {
            throw new Error('Không tìm thấy danh sách từ đã chọn. Hãy quay lại và chọn từ nhé.');
          }
        } else if (scopeType === 'LESSON') {
          const ws = await fetchLessonWords(scopeId);
          setWords(shuffle(ws));
          setScopeName(`Bài học`);
        } else if (scopeType === 'SECTION') {
          const [lessonId, section] = scopeId.split(':');
          const ws = await fetchLessonWords(lessonId, section);
          setWords(shuffle(ws));
          setScopeName(SECTION_LABELS[section] || 'Một phần của bài');
        } else if (scopeType === 'SET') {
          const s = await fetchSet(scopeId);
          setWords(shuffle(s.words || []));
          setScopeName(`Bộ thẻ "${s.name}"`);
        } else {
          throw new Error('Phạm vi luyện tập không hợp lệ.');
        }
      } catch (e) {
        setError(getErrorMessage(e, 'Không tải được từ để luyện.'));
      } finally {
        setLoading(false);
        setCardStart(Date.now());
      }
    };
    load();
  }, [scopeType, scopeId, isQuick]);

  const answer = async (correct) => {
    const word = words[idx];
    const timeMs = Date.now() - cardStart;
    // Ghi tiến trình NGAY (F-027) — không chờ hết phiên.
    answerProgress({ wordId: word.id, correct, timeMs }).catch(() => {});
    const newItems = [...items, { wordId: word.id, correct, timeMs }];
    setItems(newItems);
    if (idx + 1 >= words.length) {
      finish(newItems);
    } else {
      setIdx(idx + 1);
      setFlipped(false);
      setCardStart(Date.now());
    }
  };

  const finish = async (allItems) => {
    setSaving(true);
    try {
      const correctCount = allItems.filter((i) => i.correct).length;
      const res = await recordSession({
        mode: 'FLASHCARD',
        scopeType,
        scopeId,
        totalItems: allItems.length,
        correctCount,
        items: allItems,
        startedAt,
        finishedAt: new Date().toISOString(),
      });
      setResult({ correctCount, total: allItems.length, streakDays: res.streakDays, items: allItems });
    } catch {
      const correctCount = allItems.filter((i) => i.correct).length;
      setResult({ correctCount, total: allItems.length, streakDays: null, items: allItems });
    } finally {
      setSaving(false);
      setFinished(true);
    }
  };

  if (loading) return <div className="page"><p className="muted">Đang chuẩn bị thẻ…</p></div>;
  if (error) return (
    <div className="page">
      <div className="alert alert-error">{error}</div>
      {isQuick ? (
        <button type="button" className="btn btn-outline" onClick={() => navigate(returnTo || -1)}>
          ✏️ Chọn lại từ
        </button>
      ) : (
        <button type="button" className="btn btn-outline" onClick={() => navigate(-1)}>
          ← Quay lại
        </button>
      )}
    </div>
  );

  if (words.length === 0) {
    return (
      <div className="page">
        <h1>Flashcard</h1>
        <p className="muted">Phạm vi này chưa có từ nào để luyện.</p>
        {isQuick ? (
          <button type="button" className="btn btn-outline" onClick={() => navigate(returnTo || -1)}>
            ✏️ Chọn lại từ
          </button>
        ) : (
          <button type="button" className="btn btn-outline" onClick={() => navigate(-1)}>Quay lại</button>
        )}
      </div>
    );
  }

  if (finished && result) {
    return (
      <div className="page result-page">
        <h1>Hoàn thành! 🎉</h1>
        <div className="result-summary">
          <div className="result-big">{result.correctCount}/{result.total}</div>
          <div className="muted">thẻ bạn đánh giá "Nhớ" {scopeName && `· ${scopeName}`}</div>
          {result.streakDays != null && <div className="streak-line">🔥 Chuỗi ngày học: {result.streakDays} ngày</div>}
        </div>
        <div className="result-actions">
          {isQuick ? (
            <button type="button" className="btn btn-outline" onClick={() => navigate(returnTo || -1)}>
              ✏️ Chọn lại từ
            </button>
          ) : (
            <>
              <a href="/on-tap" className="btn btn-outline">Ôn tập đến hạn</a>
              <a href="/bai-hoc" className="btn btn-primary">Học bài khác</a>
            </>
          )}
        </div>
      </div>
    );
  }

  const word = words[idx];

  // Hai mặt thẻ theo hướng đã chọn.
  const jaSide = (
    <>
      <div className="flash-ja">{word.kanji || word.kana}</div>
      {word.kanji && <div className="flash-kana">{word.kana}</div>}
      <div className="flash-romaji">{word.romaji}</div>
      <AudioButton url={word.audioUrl} />
    </>
  );
  const viSide = (
    <>
      <div className="flash-meaning">{word.meaningVi}</div>
      {word.pos && <div className="flash-pos">{word.pos}</div>}
      {word.exampleJa && (
        <div className="flash-example">
          <div>{word.exampleJa}</div>
          {word.exampleVi && <div className="muted">{word.exampleVi}</div>}
        </div>
      )}
    </>
  );
  const frontSide = direction === 'vi_jp' ? viSide : jaSide;
  const backSide = direction === 'vi_jp' ? jaSide : viSide;

  return (
    <div className="page practice-page">
      <div className="practice-top">
        <span className="muted">Thẻ {idx + 1}/{words.length}{scopeName && ` · ${scopeName}`}{direction === 'vi_jp' && ' · Việt → Nhật'}</span>
        <div className="progress-bar slim"><div className="progress-fill" style={{ width: `${((idx) / words.length) * 100}%` }} /></div>
      </div>

      <div className={`flash-card${flipped ? ' flipped' : ''}`} onClick={() => setFlipped(!flipped)}>
        {!flipped ? (
          <div className="flash-front">
            {frontSide}
            <div className="flash-hint">Chạm để lật thẻ</div>
          </div>
        ) : (
          <div className="flash-back">
            {backSide}
          </div>
        )}
      </div>

      <div className="flash-actions">
        <button type="button" className="btn btn-outline btn-lg" onClick={() => answer(false)} disabled={saving}>
          😕 Chưa nhớ
        </button>
        <button type="button" className="btn btn-primary btn-lg" onClick={() => answer(true)} disabled={saving}>
          😊 Nhớ rồi
        </button>
      </div>
      {!flipped && <p className="muted center">{direction === 'vi_jp' ? 'Hãy tự nhớ từ tiếng Nhật trước khi lật thẻ nhé!' : 'Hãy tự nhớ nghĩa trước khi lật thẻ nhé!'}</p>}
    </div>
  );
}
