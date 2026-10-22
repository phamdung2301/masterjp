import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { fetchLessonWords } from '../api/lessons.js';
import { fetchSet } from '../api/sets.js';
import { answerProgress, recordSession } from '../api/study.js';
import { getErrorMessage } from '../api/client.js';
import AudioButton from '../components/AudioButton.jsx';
import Confetti from '../components/Confetti.jsx';
import { CardIcon, CheckIcon, CrossIcon, RestartIcon } from '../components/icons.jsx';
import { SECTION_LABELS, shuffle } from '../utils.js';
import './pages.css';

// US-005 (F-020, F-027): flashcard — lật thẻ 3D, Nhớ/Chưa nhớ.
// PATCH /api/progress/answer NGAY sau mỗi thẻ (không dồn cuối phiên).
// Hết thẻ → POST /api/sessions → màn kết quả + confetti.
// R2: chế độ luyện tập đã chọn (?quick=1) — mirror pattern Quiz.jsx: lấy danh
// sách full word objects từ sessionStorage (key chung 'mjp_quick_quiz'), KHÔNG gọi API.
// v2.0 (A3/A5): nhúng trong Lesson Hub — truyền scopeType/scopeId/direction qua
// props thay vì query string; logic chấm điểm / SM-2 giữ nguyên 100%.
// Props:
// - scopeType, scopeId: 'LESSON'|'SECTION'|'SET' (+id) hoặc 'QUICK'/'quick'
// - direction: 'jp_vi' | 'vi_jp' (mặt trước thẻ) — trước đây param bị bỏ qua, nay dùng thật
// - embedded: true khi render trong Lesson Hub (không đổi URL)
// - onExit: callback nút "thoát" khi embedded (mặc định: navigate(-1))
const QUICK_KEY = 'mjp_quick_quiz';

export default function Flashcard({
  scopeType: pScopeType,
  scopeId: pScopeId,
  direction: pDirection,
  embedded = false,
  onExit,
}) {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const isQuick = embedded ? pScopeType === 'QUICK' : params.get('quick') === '1';
  const scopeType = pScopeType ?? (isQuick ? 'QUICK' : params.get('scopeType'));
  const scopeId = pScopeId ?? (isQuick ? 'quick' : params.get('scopeId'));
  const direction = ((pDirection ?? params.get('direction')) || 'jp_vi').toLowerCase();
  const viFirst = direction === 'vi_jp';

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

  const goBack = () => {
    if (onExit) { onExit(); return; }
    navigate(returnTo || -1);
  };

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
          setScopeName('Bài học');
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

  // Luyện lại: onlyWrong=true → chỉ các từ bị đánh "Chưa nhớ".
  const restart = (onlyWrong) => {
    const wrongIds = new Set(items.filter((i) => !i.correct).map((i) => i.wordId));
    const nextWords = shuffle(onlyWrong ? words.filter((w) => wrongIds.has(w.id)) : words);
    setWords(nextWords);
    setItems([]);
    setIdx(0);
    setFlipped(false);
    setFinished(false);
    setResult(null);
    setSaving(false);
    setCardStart(Date.now());
  };

  if (loading) return <div className="page"><p className="muted">Đang chuẩn bị thẻ…</p></div>;
  if (error) return (
    <div className="page">
      <div className="alert alert-error">{error}</div>
      <button type="button" className="btn btn-outline" onClick={goBack}>
        <CardIcon size={16} /> Chọn lại từ
      </button>
    </div>
  );

  if (words.length === 0) {
    return (
      <div className="page">
        <h1>Flashcard</h1>
        <p className="muted">Phạm vi này chưa có từ nào để luyện.</p>
        <button type="button" className="btn btn-outline" onClick={goBack}>Quay lại</button>
      </div>
    );
  }

  if (finished && result) {
    const wrongCount = (result.items || []).filter((i) => !i.correct).length;
    return (
      <div className="page result-page">
        <Confetti run />
        <h1>Hoàn thành!</h1>
        <div className="result-summary">
          <div className="result-big">{result.correctCount}/{result.total}</div>
          <div className="muted">thẻ bạn đánh giá "Nhớ" {scopeName && `· ${scopeName}`}</div>
          {result.streakDays != null && <div className="streak-line">Chuỗi ngày học: {result.streakDays} ngày</div>}
        </div>
        <div className="result-actions">
          <button type="button" className="btn btn-primary" onClick={() => restart(false)}>
            <RestartIcon size={16} /> Luyện lại toàn bộ
          </button>
          {wrongCount > 0 ? (
            <button type="button" className="btn btn-outline" onClick={() => restart(true)}>
              <CardIcon size={16} /> Luyện lại {wrongCount} từ chưa nhớ
            </button>
          ) : (
            <span className="muted">Tuyệt vời, nhớ hết rồi!</span>
          )}
          {isQuick ? (
            <button type="button" className="btn btn-outline" onClick={goBack}>
              <CardIcon size={16} /> Chọn lại từ
            </button>
          ) : (
            <>
              <Link to="/on-tap" className="btn btn-outline">Ôn tập đến hạn</Link>
              <Link to="/hoc" className="btn btn-primary">Học bài khác</Link>
            </>
          )}
        </div>
      </div>
    );
  }

  const word = words[idx];

  // Mặt trước/sau theo hướng thẻ (jp_vi mặc định; vi_jp đảo lại).
  const frontJa = (
    <>
      <div className="flash-ja">{word.kanji || word.kana}</div>
      {word.kanji && <div className="flash-kana">{word.kana}</div>}
      <div className="flash-romaji">{word.romaji}</div>
      <AudioButton url={word.audioUrl} />
    </>
  );
  const frontVi = <div className="flash-meaning" style={{ color: '#fff' }}>{word.meaningVi}</div>;
  const backJa = (
    <>
      <div className="flash-ja">{word.kanji || word.kana}</div>
      {word.kanji && <div className="flash-kana">{word.kana}</div>}
      <div className="flash-romaji">{word.romaji}</div>
      {word.exampleJa && (
        <div className="flash-example">
          <div>{word.exampleJa}</div>
          {word.exampleVi && <div className="muted">{word.exampleVi}</div>}
        </div>
      )}
    </>
  );
  const backVi = (
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

  return (
    <div className="page practice-page">
      <div className="practice-top">
        <span className="muted">Thẻ {idx + 1}/{words.length}{scopeName && ` · ${scopeName}`}</span>
        <div className="progress-bar slim"><div className="progress-fill" style={{ width: `${((idx) / words.length) * 100}%` }} /></div>
      </div>

      {/* A5: lật 3D — .fc-scene(perspective) > .fc-3d(preserve-3d) > .fc-face(front/back). */}
      <div className="fc-scene">
        <div className={`fc-3d${flipped ? ' fc-flipped' : ''}`} onClick={() => setFlipped(!flipped)} role="button" aria-label="Lật thẻ">
          <div className="fc-face fc-front">
            {viFirst ? frontVi : frontJa}
            <div className="flash-hint">Chạm để lật thẻ</div>
          </div>
          <div className="fc-face fc-back">
            {viFirst ? backJa : backVi}
          </div>
        </div>
      </div>

      <div className="grade-row">
        <button type="button" className="gbtn no" onClick={() => answer(false)} disabled={saving}>
          <CrossIcon size={18} /> Chưa nhớ
        </button>
        <button type="button" className="gbtn yes" onClick={() => answer(true)} disabled={saving}>
          <CheckIcon size={18} /> Nhớ rồi
        </button>
      </div>
      {!flipped && <p className="muted center">Hãy tự nhớ nghĩa trước khi lật thẻ nhé!</p>}
    </div>
  );
}
