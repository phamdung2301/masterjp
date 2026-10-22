import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { answerProgress, fetchDueWords, recordSession } from '../api/study.js';
import { getErrorMessage } from '../api/client.js';
import AudioButton from '../components/AudioButton.jsx';
import Confetti from '../components/Confetti.jsx';
import { CheckIcon, CrossIcon, FlameIcon } from '../components/icons.jsx';
import './pages.css';

// US-008 (F-026, F-027): ôn từ đến hạn theo SM-2, kiểu flashcard.
// PATCH progress ngay mỗi thẻ; hết → POST sessions (mode REVIEW_DUE).
// v2.0 (A3/A5): lật thẻ 3D + confetti khi xong; icon SVG thay emoji.
// 3.4: gom hiển thị — số từ đến hạn + thời gian ước tính + streak (logic SM-2 giữ nguyên).
export default function ReviewDue() {
  const [words, setWords] = useState([]);
  const [idx, setIdx] = useState(0);
  const [flipped, setFlipped] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [items, setItems] = useState([]);
  const [finished, setFinished] = useState(false);
  const [result, setResult] = useState(null);
  const [saving, setSaving] = useState(false);
  const [cardStart, setCardStart] = useState(Date.now());
  const startedAt = useMemo(() => new Date().toISOString(), []);

  useEffect(() => {
    setLoading(true);
    fetchDueWords(50)
      .then((data) => {
        setWords(Array.isArray(data) ? data : data.words || []);
        setCardStart(Date.now());
      })
      .catch((e) => setError(getErrorMessage(e, 'Không tải được danh sách từ đến hạn.')))
      .finally(() => setLoading(false));
  }, []);

  const answer = async (correct) => {
    const word = words[idx];
    const timeMs = Date.now() - cardStart;
    answerProgress({ wordId: word.id, correct, timeMs }).catch(() => {});
    const newItems = [...items, { wordId: word.id, correct, timeMs }];
    setItems(newItems);
    if (idx + 1 >= words.length) {
      setSaving(true);
      try {
        const correctCount = newItems.filter((i) => i.correct).length;
        const res = await recordSession({
          mode: 'REVIEW_DUE',
          scopeType: 'REVIEW',
          scopeId: 'due',
          totalItems: newItems.length,
          correctCount,
          items: newItems,
          startedAt,
          finishedAt: new Date().toISOString(),
        });
        setResult({ correctCount, total: newItems.length, streakDays: res.streakDays });
      } catch {
        const correctCount = newItems.filter((i) => i.correct).length;
        setResult({ correctCount, total: newItems.length, streakDays: null });
      } finally {
        setSaving(false);
        setFinished(true);
      }
    } else {
      setIdx(idx + 1);
      setFlipped(false);
      setCardStart(Date.now());
    }
  };

  if (loading) return <div className="page"><p className="muted">Đang tải từ đến hạn…</p></div>;
  if (error) return <div className="page"><div className="alert alert-error">{error}</div></div>;

  if (finished && result) {
    return (
      <div className="page result-page">
        <Confetti run />
        <h1>Ôn tập xong!</h1>
        <div className="result-summary">
          <div className="result-big">{result.correctCount}/{result.total}</div>
          <div className="muted">từ bạn còn nhớ</div>
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

  if (words.length === 0) {
    return (
      <div className="page">
        <h1>Ôn tập đến hạn</h1>
        <div className="empty-state">
          <div className="empty-icon"><span className="jp-badge matcha">復</span></div>
          <p>Không có từ nào đến hạn ôn hôm nay. Bạn đang theo kịp tiến độ — tuyệt vời!</p>
          <Link to="/hoc" className="btn btn-primary">Học từ mới</Link>
        </div>
      </div>
    );
  }

  const word = words[idx];
  // Ước tính: ~8 giây / từ còn lại.
  const etaMin = Math.max(1, Math.round(((words.length - idx) * 8) / 60));

  return (
    <div className="page practice-page">
      <div className="practice-top">
        <span className="muted">Từ {idx + 1}/{words.length} đến hạn ôn · còn khoảng {etaMin} phút</span>
        <div className="progress-bar slim"><div className="progress-fill" style={{ width: `${(idx / words.length) * 100}%` }} /></div>
      </div>
      {/* A5: lật thẻ 3D — .fc-scene(perspective) > .fc-3d(preserve-3d) > .fc-face(front/back). */}
      <div className="fc-scene">
        <div className={`fc-3d${flipped ? ' fc-flipped' : ''}`} onClick={() => setFlipped(!flipped)} role="button" aria-label="Lật thẻ">
          <div className="fc-face fc-front">
            <div className="flash-ja">{word.kanji || word.kana}</div>
            {word.kanji && <div className="flash-kana">{word.kana}</div>}
            <div className="flash-romaji">{word.romaji}</div>
            <AudioButton url={word.audioUrl} />
            <div className="flash-hint">Chạm để lật thẻ</div>
          </div>
          <div className="fc-face fc-back">
            <div className="flash-meaning">{word.meaningVi}</div>
            {word.exampleJa && (
              <div className="flash-example">
                <div>{word.exampleJa}</div>
                {word.exampleVi && <div className="muted">{word.exampleVi}</div>}
              </div>
            )}
          </div>
        </div>
      </div>
      <div className="grade-row">
        <button type="button" className="gbtn no" onClick={() => answer(false)} disabled={saving}>
          <CrossIcon size={18} /> Quên rồi
        </button>
        <button type="button" className="gbtn yes" onClick={() => answer(true)} disabled={saving}>
          <CheckIcon size={18} /> Còn nhớ
        </button>
      </div>
    </div>
  );
}
