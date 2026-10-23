import { useEffect, useState } from 'react';
import { fetchGrammar } from '../api/grammar.js';
import { gradeSentence, isDailyLimit } from '../api/ai.js';
import { getErrorMessage } from '../api/client.js';
import SenseiQuota from './SenseiQuota.jsx';
import './sensei.css';

// F-039 — "Viết câu": practice mode trong Lesson Hub.
// User viết 1 câu tiếng Nhật dùng điểm ngữ pháp của bài → Sensei AI chấm
// (score / câu đã sửa / danh sách lỗi span-issue-fix / giải thích tiếng Việt).
// Không ghi progress/streak theo persistence contract F-039.

const MAX_SENTENCE = 500;

export default function SentencePractice({ lessonNo, onExit }) {
  const [points, setPoints] = useState(null); // null = đang tải
  const [pointsError, setPointsError] = useState('');
  const [grammarPointId, setGrammarPointId] = useState('');
  const [sentence, setSentence] = useState('');
  const [phase, setPhase] = useState('idle'); // idle | submitting | result | error
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const [errorRetryable, setErrorRetryable] = useState(true);
  const [aiCalls, setAiCalls] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setPoints(null);
    setPointsError('');
    fetchGrammar({ lessonNo })
      .then((items) => {
        if (cancelled) return;
        const list = Array.isArray(items) ? items : [];
        setPoints(list);
        if (list.length > 0 && list[0]?.id) setGrammarPointId(String(list[0].id));
      })
      .catch((e) => {
        if (!cancelled) setPointsError(getErrorMessage(e, 'Không tải được ngữ pháp bài này.'));
      });
    return () => { cancelled = true; };
  }, [lessonNo]);

  const selected = (points || []).find((p) => String(p.id) === String(grammarPointId));

  const submit = async () => {
    const text = sentence.trim();
    if (!grammarPointId) {
      setError('Hãy chọn một điểm ngữ pháp trước.');
      setErrorRetryable(false);
      setPhase('error');
      return;
    }
    if (!text) {
      setError('Hãy nhập câu tiếng Nhật bạn muốn Sensei chấm.');
      setErrorRetryable(false);
      setPhase('error');
      return;
    }
    if (text.length > MAX_SENTENCE) {
      setError(`Câu quá dài (tối đa ${MAX_SENTENCE} ký tự). Hãy tách thành câu ngắn hơn.`);
      setErrorRetryable(false);
      setPhase('error');
      return;
    }
    setPhase('submitting');
    setError('');
    try {
      const data = await gradeSentence({ sentence: text, grammarPointId });
      setAiCalls((c) => c + 1);
      const n = Number(data?.score);
      const score = Number.isFinite(n) ? Math.max(0, Math.min(100, Math.round(n))) : null;
      const errors = Array.isArray(data?.errors)
        ? data.errors.filter((e) => e && typeof e === 'object' && (e.span || e.issue || e.fix))
        : [];
      setResult({
        score,
        corrected: typeof data?.corrected === 'string' ? data.corrected : '',
        errors,
        explanation: typeof data?.explanation === 'string' ? data.explanation : '',
      });
      setPhase('result');
    } catch (e) {
      setError(getErrorMessage(e, 'Sensei AI đang bận, vui lòng thử lại sau.'));
      setErrorRetryable(!isDailyLimit(e));
      setPhase('error');
    }
  };

  const scoreClass = result?.score == null ? 'mid'
    : result.score >= 80 ? 'good' : result.score >= 50 ? 'mid' : 'low';
  const scoreCaption = result?.score == null ? ''
    : result.score >= 80 ? 'Tuyệt vời! Câu của bạn rất chuẩn.'
    : result.score >= 50 ? 'Khá tốt — xem lại các lỗi bên dưới nhé.'
    : 'Còn nhiều lỗi — đọc kỹ phần giải thích rồi viết lại nhé.';

  return (
    <div className="sentence-wrap">
      <div className="sentence-head">
        <h3>Viết câu — Sensei chấm điểm</h3>
        <SenseiQuota refreshKey={aiCalls} />
      </div>
      <p className="muted" style={{ margin: '0 0 4px' }}>
        Viết một câu tiếng Nhật dùng điểm ngữ pháp đang học — Sensei AI sẽ chấm điểm, sửa lỗi và giải thích bằng tiếng Việt.
      </p>

      {points === null && !pointsError && <p className="muted">Đang tải ngữ pháp bài này…</p>}
      {pointsError && <div className="alert alert-error">{pointsError}</div>}

      {points && points.length === 0 && !pointsError && (
        <p className="muted">Bài này chưa có dữ liệu ngữ pháp để Sensei chấm.</p>
      )}

      {points && points.length > 0 && (
        <div className="sentence-form">
          <label>
            Điểm ngữ pháp
            <select value={grammarPointId} onChange={(e) => setGrammarPointId(e.target.value)}
              aria-label="Chọn điểm ngữ pháp" disabled={phase === 'submitting'}>
              {points.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.title}{p.titleVi ? ` — ${p.titleVi}` : ''}
                </option>
              ))}
            </select>
          </label>
          {selected?.pattern && (
            <p className="muted" style={{ margin: 0 }}>
              Mẫu câu: <code className="grammar-pattern">{selected.pattern}</code>
            </p>
          )}
          <label>
            Câu tiếng Nhật của bạn
            <textarea
              value={sentence}
              onChange={(e) => setSentence(e.target.value)}
              rows={3}
              maxLength={MAX_SENTENCE}
              placeholder="VD: 毎日日本語を勉強しています。"
              disabled={phase === 'submitting'}
              aria-label="Nhập câu tiếng Nhật cần chấm"
            />
          </label>
          <div className="char-count">{sentence.length}/{MAX_SENTENCE}</div>
          <div className="sentence-actions">
            <button type="button" className="btn btn-primary"
              onClick={submit} disabled={phase === 'submitting' || !sentence.trim()}>
              {phase === 'submitting' ? 'Sensei đang chấm…' : 'Gửi Sensei chấm'}
            </button>
            {onExit && (
              <button type="button" className="btn btn-outline" onClick={onExit}>
                ← Thoát luyện tập
              </button>
            )}
          </div>
        </div>
      )}

      {phase === 'error' && (
        <>
          <div className="alert alert-error">{error}</div>
          {errorRetryable && (
            <div className="sentence-actions">
              <button type="button" className="btn btn-outline btn-sm" onClick={submit}>
                Thử lại
              </button>
            </div>
          )}
        </>
      )}

      {phase === 'result' && result && (
        <div className="sentence-result">
          <div className="score-row">
            <div className={`score-badge ${scoreClass}`} aria-label={`Điểm ${result.score ?? '—'} trên 100`}>
              <b>{result.score ?? '—'}</b>
              <small>/100</small>
            </div>
            <div className="score-caption">{scoreCaption}</div>
          </div>

          {result.corrected && (
            <div className="grade-corrected">
              <span className="grade-label">Câu đã sửa</span>
              <p>{result.corrected}</p>
            </div>
          )}

          {result.errors.length > 0 ? (
            <div>
              <span className="grade-label">Các lỗi ({result.errors.length})</span>
              <ul className="grade-errors">
                {result.errors.map((er, i) => (
                  <li key={i} className="grade-error">
                    {er.span && <div className="grade-span">“{er.span}”</div>}
                    {er.issue && <div className="grade-issue">{er.issue}</div>}
                    {er.fix && <div className="grade-fix">Sửa thành: <b>{er.fix}</b></div>}
                  </li>
                ))}
              </ul>
            </div>
          ) : (
            <p className="grade-ok">Không phát hiện lỗi nào trong câu của bạn.</p>
          )}

          {result.explanation && (
            <div className="grade-explain">
              <span className="grade-label">Giải thích</span>
              <p style={{ margin: 0 }}>{result.explanation}</p>
            </div>
          )}

          <div className="sentence-actions">
            <button type="button" className="btn btn-outline btn-sm"
              onClick={() => { setPhase('idle'); setResult(null); setSentence(''); }}>
              Viết câu khác
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
