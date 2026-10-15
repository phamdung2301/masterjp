import { useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { answerProgress, generateQuiz, recordSession } from '../api/study.js';
import { getErrorMessage } from '../api/client.js';
import { playAudio, resolveAssetUrl, QUIZ_MODE_LABELS } from '../utils.js';
import './pages.css';

// US-006 (F-021/022/023, F-027): quiz 20 câu, chấm tại chỗ, PATCH progress mỗi câu.
// mode listening: phát audio thay vì hiện chữ, preload câu kế (NFR-001).
const MODE_MAP = { jp_vi: 'JP_VI', vi_jp: 'VI_JP', listening: 'LISTENING' };
const SESSION_MODE = { JP_VI: 'QUIZ_JP_VI', VI_JP: 'QUIZ_VI_JP', LISTENING: 'LISTENING' };

export default function Quiz() {
  const [params] = useSearchParams();
  const mode = MODE_MAP[params.get('mode')] || 'JP_VI';
  const scopeType = params.get('scopeType');
  const scopeId = params.get('scopeId');

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
  const startedAt = useMemo(() => new Date().toISOString(), []);
  const audioCache = useRef({});

  useEffect(() => {
    setLoading(true);
    setError('');
    generateQuiz({ mode, scopeType, scopeId, limit: 20 })
      .then((data) => {
        const qs = data.questions || [];
        if (qs.length < 4) throw new Error('Phạm vi này không đủ từ để tạo quiz (cần ít nhất 4 từ). Hãy mở rộng phạm vi nhé.');
        setQuestions(qs);
        setQStart(Date.now());
      })
      .catch((e) => setError(getErrorMessage(e, 'Không tạo được bộ câu hỏi.')))
      .finally(() => setLoading(false));
  }, [mode, scopeType, scopeId]);

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
      setResult({ correctCount, total: allItems.length, streakDays: res.streakDays, wrong });
    } catch {
      setResult({ correctCount, total: allItems.length, streakDays: null, wrong });
    } finally {
      setSaving(false);
      setFinished(true);
    }
  };

  if (loading) return <div className="page"><p className="muted">Đang tạo bộ câu hỏi…</p></div>;
  if (error) return <div className="page"><div className="alert alert-error">{error}</div></div>;

  if (finished && result) {
    const pctScore = Math.round((result.correctCount / result.total) * 100);
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
        <div className="result-actions">
          <a href="/on-tap" className="btn btn-outline">Ôn tập đến hạn</a>
          <a href="/bai-hoc" className="btn btn-primary">Học bài khác</a>
        </div>
      </div>
    );
  }

  const q = questions[idx];
  if (!q) return null;

  return (
    <div className="page practice-page">
      <div className="practice-top">
        <span className="muted">Câu {idx + 1}/{questions.length} · {QUIZ_MODE_LABELS[mode]}</span>
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
