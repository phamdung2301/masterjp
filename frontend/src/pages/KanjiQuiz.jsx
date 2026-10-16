import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { toHiragana } from 'wanakana';
import { generateKanjiQuiz } from '../api/kanji.js';
import { getErrorMessage } from '../api/client.js';
import './pages.css';

// R3 (F-D): quiz kanji — trắc nghiệm (KANJI_MEANING / MEANING_KANJI) + gõ cách
// đọc (KANJI_TYPING) trong 1 trang, chấm tại chỗ.
//
// - v1 KHÔNG lưu SM-2 / progress: POST /api/kanji/quiz chỉ sinh câu hỏi (không
//   tạo StudySession, không cập nhật WordProgress) — nên trang này KHÔNG gọi
//   answerProgress / recordSession. Kanji progress để phase 2.
// - Không tái dùng Quiz.jsx / Typing.jsx để tránh ảnh hưởng luồng từ vựng.
// - Gõ cách đọc: `kana` là chuỗi nối mọi on/kun (vd. "シ, わたし, わたくし"),
//   chấp nhận bất kỳ cách đọc nào trong đó. Normalize: trim/lowercase,
//   romaji→hiragana (wanakana), bỏ ký tự ngoặc (giữ phần chữ bên trong).
//   Gõ rút gọn `ひと` cho data `ひと(つ)` → SAI (strict theo data — chủ ý).
const KANJI_QUIZ_KEY = 'mjp_kanji_quiz';

const MODE_LABEL = {
  KANJI_MEANING: 'Chữ → Nghĩa',
  MEANING_KANJI: 'Nghĩa → Chữ',
  KANJI_TYPING: 'Gõ cách đọc',
};

// Chuẩn hóa cách đọc để chấm: data "ひと(つ)" → "ひとつ" nên user gõ
// "ひとつ" / "ひと(つ)" / "hitotsu" đều đúng; gõ "ひと" → SAI.
function normReading(s) {
  return toHiragana(String(s || '').trim().toLowerCase())
    .replace(/[（）]/g, (m) => (m === '（' ? '(' : ')'))
    .replace(/[()]/g, '')
    .replace(/[\s　・〜～~-‐]/g, '');
}

function acceptedReadings(kana) {
  return String(kana || '').split(/[,、]/).map(normReading).filter(Boolean);
}

function checkTyping(input, kana) {
  const n = normReading(input);
  if (!n) return false;
  return acceptedReadings(kana).includes(n);
}

export default function KanjiQuiz() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const [payload, setPayload] = useState(null); // { questions, mode, count }
  const [loadError, setLoadError] = useState('');
  const [idx, setIdx] = useState(0);
  const [picked, setPicked] = useState(null); // wordId của option đã chọn (trắc nghiệm)
  const [input, setInput] = useState(''); // gõ cách đọc
  const [checked, setChecked] = useState(null); // null | { correct: bool }
  const [answers, setAnswers] = useState([]); // [{ wordId, correct }]
  const [finished, setFinished] = useState(false);
  const [regenerating, setRegenerating] = useState(false);
  const [regenError, setRegenError] = useState('');
  const inputRef = useRef(null);

  useEffect(() => {
    try {
      const raw = sessionStorage.getItem(KANJI_QUIZ_KEY);
      const data = raw ? JSON.parse(raw) : null;
      const questions = data?.questions || [];
      if (questions.length === 0) {
        setLoadError('Chưa có bộ câu hỏi. Hãy tick chọn chữ kanji rồi bấm luyện tập.');
        return;
      }
      setPayload({ questions, mode: data.mode || params.get('mode') || 'KANJI_MEANING' });
    } catch {
      setLoadError('Không đọc được bộ câu hỏi đã lưu.');
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (payload && !finished && payload.mode === 'KANJI_TYPING' && !checked && inputRef.current) {
      inputRef.current.focus();
    }
  }, [payload, finished, idx, checked]);

  if (loadError || !payload) {
    return (
      <div className="page">
        <h1>✏️ Quiz kanji</h1>
        {loadError && <div className="alert alert-error">{loadError}</div>}
        <Link to="/kanji" className="btn btn-primary">← Về trang Kanji</Link>
      </div>
    );
  }

  const { questions, mode } = payload;
  const isTyping = mode === 'KANJI_TYPING';
  const q = questions[idx];
  const correctCount = answers.filter((a) => a.correct).length;

  const resetForNewSet = (data, newMode) => {
    const qs = data?.questions || [];
    if (qs.length === 0) throw new Error('Không tạo được câu hỏi cho tập chữ này.');
    const next = { questions: qs, mode: newMode, count: qs.length };
    setPayload(next);
    sessionStorage.setItem(KANJI_QUIZ_KEY, JSON.stringify(next));
    setIdx(0);
    setPicked(null);
    setInput('');
    setChecked(null);
    setAnswers([]);
    setFinished(false);
  };

  const regenerate = async (chars) => {
    setRegenError('');
    if (chars.length === 0) return;
    setRegenerating(true);
    try {
      const data = await generateKanjiQuiz({ characters: chars, mode });
      resetForNewSet(data, mode);
    } catch (e) {
      setRegenError(getErrorMessage(e, 'Không tạo lại được bộ câu hỏi.'));
    } finally {
      setRegenerating(false);
    }
  };

  const answerMcq = (optWordId) => {
    if (checked) return;
    const correct = optWordId === q.answerWordId;
    setPicked(optWordId);
    setChecked({ correct });
    setAnswers((prev) => [...prev, { wordId: q.wordId, correct }]);
  };

  const checkInput = () => {
    if (checked || !input.trim()) return;
    const correct = checkTyping(input, q.kana);
    setChecked({ correct });
    setAnswers((prev) => [...prev, { wordId: q.wordId, correct }]);
  };

  const next = () => {
    if (idx + 1 >= questions.length) {
      setFinished(true);
    } else {
      setIdx(idx + 1);
      setPicked(null);
      setInput('');
      setChecked(null);
    }
  };

  const handleInputChange = (e) => {
    if (checked) return;
    // Romaji → hiragana realtime; ký tự Nhật gõ trực tiếp được giữ nguyên.
    setInput(toHiragana(e.target.value, { IMEMode: true }));
  };

  if (finished) {
    const wrongChars = answers.filter((a) => !a.correct).map((a) => a.wordId);
    const pct = questions.length > 0 ? Math.round((correctCount / questions.length) * 100) : 0;
    return (
      <div className="page">
        <h1>✏️ Quiz kanji — Kết quả</h1>
        <div className="kquiz-result">
          <div className="kquiz-score">{correctCount}/{questions.length}</div>
          <p className="muted">Đúng {pct}% · {MODE_LABEL[mode] || mode}</p>
          <p className="muted kquiz-note">v1 chưa lưu tiến trình SM-2 cho kanji (theo contract F-D).</p>
          {regenError && <div className="alert alert-error">{regenError}</div>}
          <div className="kquiz-result-actions">
            <button
              type="button" className="btn btn-outline"
              disabled={wrongChars.length === 0 || regenerating}
              onClick={() => regenerate(wrongChars)}
            >
              {regenerating ? 'Đang tạo…' : `🔁 Luyện lại ${wrongChars.length} chữ sai`}
            </button>
            <button
              type="button" className="btn btn-outline"
              disabled={regenerating}
              onClick={() => regenerate(questions.map((x) => x.wordId))}
            >
              {regenerating ? 'Đang tạo…' : '🔁 Luyện lại toàn bộ'}
            </button>
            <button type="button" className="btn btn-primary" onClick={() => navigate('/kanji')}>
              ← Về trang Kanji
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="page">
      <h1>✏️ Quiz kanji <span className="muted kquiz-mode-label">· {MODE_LABEL[mode] || mode}</span></h1>
      <div className="kquiz-progress">
        <span>Câu {idx + 1}/{questions.length}</span>
        <span className="muted">Đúng {correctCount}</span>
      </div>

      <div className="kquiz-card">
        <div className="kquiz-prompt">{q.promptText}</div>
        {isTyping && q.meaningVi && <div className="kquiz-meaning">{q.meaningVi}</div>}

        {isTyping ? (
          <div className="kquiz-typing">
            <input
              ref={inputRef}
              type="text"
              className="kquiz-input"
              placeholder="Gõ cách đọc (romaji hoặc kana)…"
              value={input}
              onChange={handleInputChange}
              onKeyDown={(e) => { if (e.key === 'Enter') checkInput(); }}
              disabled={!!checked}
              autoComplete="off"
            />
            {!checked ? (
              <button type="button" className="btn btn-primary" onClick={checkInput} disabled={!input.trim()}>
                Kiểm tra
              </button>
            ) : (
              <div className={`kquiz-feedback${checked.correct ? ' correct' : ' wrong'}`}>
                {checked.correct ? '✅ Đúng!' : (
                  <>❌ Sai. Các cách đọc: <strong>{q.kana}</strong></>
                )}
              </div>
            )}
          </div>
        ) : (
          <div className="kquiz-options">
            {(q.options || []).map((opt) => {
              const isAnswer = opt.wordId === q.answerWordId;
              const isPicked = opt.wordId === picked;
              let cls = 'kquiz-opt';
              if (checked) {
                if (isAnswer) cls += ' correct';
                else if (isPicked) cls += ' wrong';
                else cls += ' dimmed';
              }
              return (
                <button
                  key={opt.wordId}
                  type="button"
                  className={cls}
                  disabled={!!checked}
                  onClick={() => answerMcq(opt.wordId)}
                >
                  {opt.text}
                </button>
              );
            })}
          </div>
        )}

        {checked && (
          <div className="kquiz-next-row">
            <button type="button" className="btn btn-primary" onClick={next}>
              {idx + 1 >= questions.length ? 'Xem kết quả →' : 'Câu tiếp →'}
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
