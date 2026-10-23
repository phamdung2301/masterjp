import { useEffect, useState } from 'react';
import { generateExercises, isDailyLimit } from '../api/ai.js';
import { getErrorMessage } from '../api/client.js';
import SenseiQuota from './SenseiQuota.jsx';
import './sensei.css';

// F-040 — "Luyện ngay": sinh bài tập trắc nghiệm/điền từ bằng AI cho 1 điểm ngữ pháp.
// Thay thế fallback quiz từ vựng tạm thời. Không persist, không ghi progress (contract F-040).

const COUNT = 10;

// Validate defensive từng exercise theo contract §5.1 — sai format → bỏ câu đó
// (BE phải 502 khi AI trả sai schema, nhưng FE vẫn guard để không crash khi render).
function normalizeExercise(raw) {
  if (!raw || typeof raw !== 'object') return null;
  const type = raw.type === 'fill' ? 'fill' : 'choice';
  const question = typeof raw.question === 'string' ? raw.question : '';
  const answer = typeof raw.answer === 'string' ? raw.answer : '';
  const explanation = typeof raw.explanation === 'string' ? raw.explanation : '';
  if (!question || !answer) return null;
  if (type === 'choice') {
    const choices = Array.isArray(raw.choices) ? raw.choices.filter((c) => typeof c === 'string') : [];
    if (choices.length !== 4 || !choices.includes(answer)) return null;
    return { type, question, choices, answer, explanation };
  }
  if (!question.includes('___')) return null;
  return { type, question, answer, explanation };
}

// Render "___" trong câu hỏi thành ô trống trực quan.
function renderQuestion(question, keyPrefix) {
  const parts = String(question).split('___');
  return parts.map((p, i) => (
    <span key={`${keyPrefix}-p${i}`}>
      {p}
      {i < parts.length - 1 && <span className="blank" aria-hidden="true" />}
    </span>
  ));
}

export default function AiExercises({ grammarPoint, onExit }) {
  const [phase, setPhase] = useState('loading'); // loading | ready | error
  const [exercises, setExercises] = useState([]);
  const [answers, setAnswers] = useState({}); // idx → { value, correct }
  const [fillInputs, setFillInputs] = useState({}); // idx → string
  const [error, setError] = useState('');
  const [errorRetryable, setErrorRetryable] = useState(true);
  const [aiCalls, setAiCalls] = useState(0);

  const pointId = grammarPoint?.id;

  const load = async () => {
    if (!pointId) {
      setError('Thiếu điểm ngữ pháp để tạo bài tập.');
      setErrorRetryable(false);
      setPhase('error');
      return;
    }
    setPhase('loading');
    setError('');
    try {
      const data = await generateExercises({ grammarPointId: pointId, count: COUNT });
      setAiCalls((c) => c + 1);
      const list = (Array.isArray(data?.exercises) ? data.exercises : [])
        .map(normalizeExercise)
        .filter(Boolean);
      if (list.length === 0) throw new Error('Sensei AI tạo đề bị lỗi định dạng, hãy thử tạo lại.');
      setExercises(list);
      setAnswers({});
      setFillInputs({});
      setPhase('ready');
    } catch (e) {
      // F-045: hết quota AI (429 DAILY_LIMIT) → interceptor đã mở PaywallModal;
      // chỉ báo ngắn gọn, không lặp lại message thô.
      const quotaOut = isDailyLimit(e);
      setError(
        quotaOut
          ? 'Hết lượt dùng AI hôm nay — Tanu-chan vừa mở bảng nâng cấp, bạn xem thử nhé!'
          : getErrorMessage(e, 'Sensei AI đang bận, vui lòng thử lại sau.')
      );
      setErrorRetryable(!quotaOut);
      setPhase('error');
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pointId]);

  const answerChoice = (idx, choice) => {
    if (answers[idx]) return;
    const correct = choice === exercises[idx].answer;
    setAnswers((prev) => ({ ...prev, [idx]: { value: choice, correct } }));
  };

  const answerFill = (idx) => {
    if (answers[idx]) return;
    const value = (fillInputs[idx] || '').trim();
    if (!value) return;
    const correct = value === exercises[idx].answer;
    setAnswers((prev) => ({ ...prev, [idx]: { value, correct } }));
  };

  const doneCount = Object.keys(answers).length;
  const correctCount = Object.values(answers).filter((a) => a.correct).length;
  const allDone = exercises.length > 0 && doneCount === exercises.length;

  return (
    <div>
      <div className="ai-ex-head">
        <h3>
          Luyện tập: {grammarPoint?.title || 'ngữ pháp'}
          {grammarPoint?.titleVi ? ` — ${grammarPoint.titleVi}` : ''}
        </h3>
        <SenseiQuota refreshKey={aiCalls} />
        {onExit && (
          <button type="button" className="btn btn-outline btn-sm" onClick={onExit}>
            ← Quay lại
          </button>
        )}
      </div>
      <p className="muted" style={{ margin: '0 0 4px' }}>
        {COUNT} câu do Sensei AI soạn riêng cho điểm ngữ pháp này — trả lời từng câu, xem giải thích ngay.
      </p>

      {phase === 'loading' && <p className="muted">Sensei AI đang soạn bài tập…</p>}

      {phase === 'error' && (
        <>
          <div className="alert alert-error">{error}</div>
          <div className="sentence-actions">
            {errorRetryable && (
              <button type="button" className="btn btn-primary btn-sm" onClick={load}>
                Thử lại
              </button>
            )}
            {onExit && (
              <button type="button" className="btn btn-outline btn-sm" onClick={onExit}>
                ← Quay lại
              </button>
            )}
          </div>
        </>
      )}

      {phase === 'ready' && (
        <>
          {allDone && (
            <div className="ai-ex-summary" role="status">
              Bạn đúng {correctCount}/{exercises.length} câu
              {correctCount === exercises.length
                ? ' — xuất sắc!'
                : correctCount >= Math.ceil(exercises.length / 2)
                  ? ' — khá tốt, xem lại các câu sai nhé.'
                  : ' — đọc lại phần ngữ pháp rồi làm bộ đề mới nhé.'}
            </div>
          )}

          {exercises.map((ex, idx) => {
            const ans = answers[idx];
            return (
              <div key={idx} className="ai-ex-card">
                <div className="ai-ex-q">
                  <span className="muted" style={{ marginRight: 8 }}>Câu {idx + 1}</span>
                  {renderQuestion(ex.question, `q${idx}`)}
                </div>

                {ex.type === 'choice' ? (
                  <div className="ai-ex-opts">
                    {ex.choices.map((c) => {
                      const isAnswer = c === ex.answer;
                      const picked = ans?.value === c;
                      const cls = !ans ? ''
                        : isAnswer ? 'correct'
                        : picked ? 'wrong' : 'dim';
                      return (
                        <button
                          key={c}
                          type="button"
                          className={`ai-ex-opt ${cls}`}
                          onClick={() => answerChoice(idx, c)}
                          disabled={!!ans}
                        >
                          {c}
                        </button>
                      );
                    })}
                  </div>
                ) : (
                  <div className="ai-ex-fill">
                    <input
                      type="text"
                      value={fillInputs[idx] || ''}
                      onChange={(e) => setFillInputs((prev) => ({ ...prev, [idx]: e.target.value }))}
                      onKeyDown={(e) => { if (e.key === 'Enter') answerFill(idx); }}
                      placeholder="Điền từ vào chỗ trống…"
                      disabled={!!ans}
                      aria-label={`Đáp án câu ${idx + 1}`}
                    />
                    <button type="button" className="btn btn-primary btn-sm"
                      onClick={() => answerFill(idx)} disabled={!!ans || !(fillInputs[idx] || '').trim()}>
                      Trả lời
                    </button>
                  </div>
                )}

                {ans && (
                  <div className={`ai-ex-feedback ${ans.correct ? 'good' : 'bad'}`}>
                    <div className="ai-ex-answer">
                      {ans.correct ? 'Chính xác!' : `Chưa đúng — đáp án: ${ex.answer}`}
                    </div>
                    {ex.explanation && <div className="ai-ex-explain">{ex.explanation}</div>}
                  </div>
                )}
              </div>
            );
          })}

          <div className="sentence-actions">
            <button type="button" className="btn btn-outline btn-sm" onClick={load}>
              Tạo bộ đề mới
            </button>
            {onExit && (
              <button type="button" className="btn btn-outline btn-sm" onClick={onExit}>
                ← Quay lại danh sách ngữ pháp
              </button>
            )}
          </div>
        </>
      )}
    </div>
  );
}
