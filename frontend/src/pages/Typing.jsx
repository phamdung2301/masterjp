import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { toHiragana } from 'wanakana';
import { answerProgress, generateQuiz, recordSession } from '../api/study.js';
import { generateQuickQuiz } from '../api/quickPractice.js';
import { getErrorMessage } from '../api/client.js';
import { playAudio } from '../utils.js';
import { CardIcon, FlameIcon, RestartIcon, SpeakerIcon } from '../components/icons.jsx';
import './pages.css';

// US-017 (F-037, F-027): gõ phiên âm — hiện kanji (+ nghĩa Việt), user gõ romaji
// tự chuyển hiragana realtime (wanakana), chấm local, PATCH progress mỗi câu.
// R2: chế độ luyện tập đã chọn (?quick=1) — mirror pattern Quiz.jsx: nhận
// wordIds từ sessionStorage (trang chi tiết bài lưu trước khi chuyển sang),
// gọi POST /api/quiz/quick với mode 'TYPING', ghi session scopeType 'QUICK'.
// v2.0 (A3): nhúng trong Lesson Hub — truyền scopeType/scopeId/quick qua props;
// logic chấm điểm / SM-2 giữ nguyên 100%.
// Props: scopeType, scopeId, quick (bool), embedded (bool), onExit (callback khi embedded).
const SESSION_MODE = 'TYPING';
const QUICK_KEY = 'mjp_quick_quiz';

// Chuẩn hóa để chấm (F-037): trim, lowercase, chuyển về hiragana, bỏ khoảng trắng
// và ký tự 〜/・. Katakana chuẩn được chấp nhận ở dạng hiragana tương đương.
function normalizeKana(s) {
  return toHiragana((s || '').trim().toLowerCase()).replace(/[\s　・〜～~\-‐]/g, '');
}

export default function Typing({
  scopeType: pScopeType,
  scopeId: pScopeId,
  quick: pQuick,
  embedded = false,
  onExit,
}) {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const isQuick = pQuick ?? (params.get('quick') === '1');
  const scopeType = pScopeType ?? (isQuick ? 'QUICK' : params.get('scopeType'));
  const scopeId = pScopeId ?? (isQuick ? 'quick' : params.get('scopeId'));

  // R2: đọc payload luyện tập đã chọn (do trang chi tiết bài lưu vào sessionStorage).
  const [quick, setQuick] = useState(() => {
    if (!isQuick) return null;
    try {
      const p = JSON.parse(sessionStorage.getItem(QUICK_KEY) || 'null');
      if (p?.wordIds?.length) return { wordIds: p.wordIds, allWordIds: p.wordIds, returnTo: p.returnTo };
    } catch { /* bỏ qua */ }
    return { error: true };
  });
  const wordIds = isQuick ? quick?.wordIds : null;
  // Non-quick: vòng luyện lại từ gõ sai dùng API quick với wordIds (ghi session QUICK).
  const [retryIds, setRetryIds] = useState(null);
  const [runKey, setRunKey] = useState(0);

  const [questions, setQuestions] = useState([]);
  const [idx, setIdx] = useState(0);
  const [input, setInput] = useState('');
  const [checked, setChecked] = useState(null); // null | { correct: bool }
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [items, setItems] = useState([]);
  const [finished, setFinished] = useState(false);
  const [result, setResult] = useState(null);
  const [saving, setSaving] = useState(false);
  const [qStart, setQStart] = useState(Date.now());
  const [startedAt, setStartedAt] = useState(() => new Date().toISOString());
  const inputRef = useRef(null);

  // Embedded (Lesson Hub): nút "chọn lại từ" thoát khỏi khối luyện tập;
  // standalone: về trang nguồn (payload returnTo) hoặc trang trước.
  const goBack = () => {
    if (onExit) { onExit(); return; }
    navigate(quick?.returnTo || -1);
  };

  useEffect(() => {
    if (isQuick && (!wordIds || wordIds.length === 0)) {
      setLoading(false);
      setError('Không tìm thấy danh sách từ đã chọn để luyện gõ. Hãy quay lại và chọn từ nhé.');
      return;
    }
    setLoading(true);
    setError('');
    const effectiveIds = isQuick ? wordIds : retryIds;
    const promise = effectiveIds
      ? generateQuickQuiz({ wordIds: effectiveIds, mode: 'TYPING' })
      : generateQuiz({ mode: 'TYPING', scopeType, scopeId, limit: 20 });
    promise
      .then((data) => {
        const qs = data.questions || [];
        if (qs.length === 0) throw new Error('Phạm vi này không có từ để luyện gõ. Hãy mở rộng phạm vi nhé.');
        setQuestions(qs);
        setQStart(Date.now());
      })
      .catch((e) => setError(getErrorMessage(e, 'Không tạo được bộ câu hỏi.')))
      .finally(() => setLoading(false));
  }, [scopeType, scopeId, isQuick, wordIds, retryIds, runKey]);

  useEffect(() => {
    if (!loading && !finished && inputRef.current) inputRef.current.focus();
  }, [idx, loading, finished]);

  // Romaji → hiragana realtime; ký tự Nhật gõ trực tiếp được giữ nguyên.
  // IMEMode: true — chữ "n" cuối chưa thành ん vội khi đang gõ dở.
  const handleChange = (e) => {
    if (checked) return;
    setInput(toHiragana(e.target.value, { IMEMode: true }));
  };

  const check = () => {
    if (checked || !input.trim()) return;
    const q = questions[idx];
    const correct = normalizeKana(input) === normalizeKana(q.kana);
    const timeMs = Date.now() - qStart;
    setChecked({ correct });
    answerProgress({ wordId: q.wordId, correct, timeMs }).catch(() => {});
    const newItems = [...items, { wordId: q.wordId, correct, timeMs }];
    setItems(newItems);
  };

  const next = () => {
    if (idx + 1 >= questions.length) {
      finish(items);
    } else {
      setIdx(idx + 1);
      setInput('');
      setChecked(null);
      setQStart(Date.now());
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      if (!checked) check();
      else next();
    }
  };

  const finish = async (allItems) => {
    setSaving(true);
    const correctCount = allItems.filter((i) => i.correct).length;
    const wrong = questions.filter((_, i) => !allItems[i]?.correct);
    // Vòng luyện lại từ gõ sai (non-quick) ghi session QUICK như luồng quick.
    const effScopeType = retryIds ? 'QUICK' : scopeType;
    const effScopeId = retryIds ? 'quick' : scopeId;
    try {
      const res = await recordSession({
        mode: SESSION_MODE,
        scopeType: effScopeType,
        scopeId: effScopeId,
        totalItems: allItems.length,
        correctCount,
        items: allItems,
        startedAt,
        finishedAt: new Date().toISOString(),
      });
      setResult({ correctCount, total: allItems.length, streakDays: res.streakDays, wrong });
    } catch {
      setResult({ correctCount, total: allItems.length, streakDays: null, wrong });
    } finally {
      setSaving(false);
      setFinished(true);
    }
  };

  // Luyện lại: newWordIds=null → toàn bộ (từ scope gốc hoặc tập đã chọn).
  const restart = (newWordIds) => {
    if (isQuick) {
      setQuick((prev) => ({ ...prev, wordIds: newWordIds || prev.allWordIds }));
    } else {
      setRetryIds(newWordIds);
    }
    setQuestions([]);
    setIdx(0);
    setInput('');
    setChecked(null);
    setItems([]);
    setFinished(false);
    setResult(null);
    setSaving(false);
    setError('');
    setQStart(Date.now());
    setStartedAt(new Date().toISOString());
    setRunKey((k) => k + 1);
  };

  if (loading) return <div className="page"><p className="muted">Đang chuẩn bị bài gõ…</p></div>;
  if (error) return (
    <div className="page">
      <div className="alert alert-error">{error}</div>
      {isQuick ? (
        <button type="button" className="btn btn-outline" onClick={goBack}>
          <CardIcon size={16} /> Chọn lại từ
        </button>
      ) : (
        <button type="button" className="btn btn-outline" onClick={() => navigate(-1)}>
          ← Quay lại
        </button>
      )}
    </div>
  );

  if (finished && result) {
    const pctScore = Math.round((result.correctCount / result.total) * 100);
    const wrongIds = result.wrong.map((q) => q.wordId);
    return (
      <div className="page result-page">
        <h1>Kết quả Gõ từ</h1>
        <div className="result-summary">
          <div className="result-big">{result.correctCount}/{result.total} <span className="result-pct">({pctScore}%)</span></div>
          {result.streakDays != null && (
            <div className="streak-line"><FlameIcon size={16} /> Chuỗi ngày học: {result.streakDays} ngày</div>
          )}
        </div>
        {result.wrong.length > 0 && (
          <>
            <h3>Từ gõ sai cần ôn lại ({result.wrong.length})</h3>
            <div className="word-list">
              {result.wrong.map((q) => (
                <div key={q.wordId} className="quiz-wrong-item">
                  <div>
                    <strong>{q.kanji && q.kanji.trim() ? q.kanji : q.meaningVi}</strong>
                    <span className="quiz-wrong-answer"> → {q.kana} ({q.meaningVi})</span>
                  </div>
                  {q.promptAudioUrl && (
                    <button type="button" className="btn btn-sm btn-outline" onClick={() => playAudio(q.promptAudioUrl)}>
                      <SpeakerIcon size={14} /> Nghe lại
                    </button>
                  )}
                </div>
              ))}
            </div>
          </>
        )}
        <div className="result-actions">
          {wrongIds.length > 0 ? (
            <button type="button" className="btn btn-primary" onClick={() => restart(wrongIds)}>
              <CardIcon size={16} /> Luyện lại {wrongIds.length} từ gõ sai
            </button>
          ) : (
            <span className="muted">Tuyệt vời, nhớ hết rồi!</span>
          )}
          <button type="button" className="btn btn-outline" onClick={() => restart(null)}>
            <RestartIcon size={16} /> Luyện lại toàn bộ
          </button>
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

  const q = questions[idx];
  if (!q) return null;
  const hasKanji = q.kanji && q.kanji.trim();

  return (
    <div className="page practice-page">
      <div className="practice-top">
        <span className="muted">Từ {idx + 1}/{questions.length} · Gõ phiên âm</span>
        <div className="progress-bar slim"><div className="progress-fill" style={{ width: `${(idx / questions.length) * 100}%` }} /></div>
      </div>

      <div className="quiz-prompt">
        {hasKanji ? (
          <>
            <div className="quiz-text">{q.kanji}</div>
            <p className="typing-meaning">{q.meaningVi}</p>
          </>
        ) : (
          <div className="quiz-text typing-meaning-only">{q.meaningVi}</div>
        )}
        <p className="muted typing-hint">Gõ cách đọc bằng romaji (tự chuyển thành hiragana) hoặc gõ trực tiếp tiếng Nhật</p>
      </div>

      <div className="typing-box">
        <input
          ref={inputRef}
          className={`typing-input${checked ? (checked.correct ? ' typing-correct' : ' typing-wrong') : ''}`}
          value={input}
          onChange={handleChange}
          onKeyDown={handleKeyDown}
          placeholder="わたし…"
          autoComplete="off"
          autoCapitalize="off"
          spellCheck={false}
          disabled={!!checked || saving}
          aria-label="Gõ phiên âm"
        />
        {checked && !checked.correct && (
          <div className="typing-answer">Đáp án đúng: <strong>{q.kana}</strong></div>
        )}
        {checked && checked.correct && (
          <div className="typing-answer typing-ok">Chính xác!</div>
        )}
        <div className="typing-actions">
          {!checked ? (
            <button type="button" className="btn btn-primary" onClick={check} disabled={!input.trim() || saving}>
              Kiểm tra
            </button>
          ) : (
            <button type="button" className="btn btn-primary" onClick={next} disabled={saving}>
              {idx + 1 >= questions.length ? 'Xem kết quả' : 'Tiếp'}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
