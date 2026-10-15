import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { fetchLesson, fetchLessonWords } from '../api/lessons.js';
import { getErrorMessage } from '../api/client.js';
import { useAuth } from '../context/AuthContext.jsx';
import WordCard from '../components/WordCard.jsx';
import AudioButton from '../components/AudioButton.jsx';
import LoginPrompt from '../components/LoginPrompt.jsx';
import AddToSetModal from '../components/AddToSetModal.jsx';
import { QUIZ_MODES, SECTION_LABELS, SECTION_TABS, STATUS_COLORS, STATUS_LABELS } from '../utils.js';
import './pages.css';

// US-003 (F-011, F-013): chi tiết bài — 3 tab phần, mỗi từ có nút phát audio,
// tick chọn nhiều từ → thêm vào bộ thẻ, nút vào flashcard/quiz theo bài hoặc từng tab.
// US-016: guest xem read-only; bấm "Luyện tập"/"Thêm vào bộ thẻ" → mời đăng nhập.
export default function LessonDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { isLoggedIn } = useAuth();
  const [lesson, setLesson] = useState(null);
  const [section, setSection] = useState('VOCAB');
  const [words, setWords] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [selected, setSelected] = useState({});
  const [loginPrompt, setLoginPrompt] = useState('');
  const [addSetOpen, setAddSetOpen] = useState(false);
  const [quizMode, setQuizMode] = useState('JP_VI');

  useEffect(() => {
    setLoading(true);
    setError('');
    Promise.all([fetchLesson(id), fetchLessonWords(id, section)])
      .then(([l, w]) => { setLesson(l); setWords(w); setSelected({}); })
      .catch((e) => setError(getErrorMessage(e, 'Không tải được bài học.')))
      .finally(() => setLoading(false));
  }, [id, section]);

  const toggleSelect = useCallback((word) => {
    setSelected((prev) => {
      const next = { ...prev };
      if (next[word.id]) delete next[word.id];
      else next[word.id] = word;
      return next;
    });
  }, []);

  const selectedWords = Object.values(selected);

  const requireLogin = (message, fn) => {
    if (!isLoggedIn) {
      setLoginPrompt(message);
      return;
    }
    fn();
  };

  const goFlashcard = (scopeType, scopeId) =>
    navigate(`/luyen-tap/flashcard?scopeType=${scopeType}&scopeId=${encodeURIComponent(scopeId)}`);

  const goQuiz = (scopeType, scopeId) =>
    navigate(`/luyen-tap/quiz?mode=${quizMode.toLowerCase()}&scopeType=${scopeType}&scopeId=${encodeURIComponent(scopeId)}`);

  const goTyping = (scopeType, scopeId) =>
    navigate(`/luyen-tap/go-phien-am?scopeType=${scopeType}&scopeId=${encodeURIComponent(scopeId)}`);

  const sectionId = `${id}:${section}`;

  if (loading) return <div className="page"><p className="muted">Đang tải…</p></div>;

  return (
    <div className="page">
      {error && <div className="alert alert-error">{error}</div>}
      {lesson && (
        <>
          <Link to="/bai-hoc" className="back-link">← Tất cả bài học</Link>
          <div className="lesson-header">
            <div>
              <h1>Bài {lesson.lessonNo} — {lesson.titleVi}</h1>
              <div className="muted">{lesson.titleJa} · Cấp độ {lesson.level}</div>
            </div>
          </div>

          <div className="practice-bar">
            <div className="practice-group">
              <span className="practice-label">Luyện cả bài:</span>
              <button type="button" className="btn btn-primary btn-sm"
                onClick={() => requireLogin('Đăng nhập để luyện flashcard và lưu tiến trình.', () => goFlashcard('LESSON', id))}>
                🃏 Flashcard
              </button>
              <select className="select-inline" value={quizMode} onChange={(e) => setQuizMode(e.target.value)} aria-label="Dạng quiz">
                {QUIZ_MODES.map((m) => <option key={m.value} value={m.value}>{m.label}</option>)}
              </select>
              <button type="button" className="btn btn-outline btn-sm"
                onClick={() => requireLogin('Đăng nhập để làm quiz và lưu tiến trình.', () => goQuiz('LESSON', id))}>
                ✏️ Quiz
              </button>
              <button type="button" className="btn btn-outline btn-sm"
                onClick={() => requireLogin('Đăng nhập để luyện gõ phiên âm và lưu tiến trình.', () => goTyping('LESSON', id))}>
                ⌨️ Gõ phiên âm
              </button>
            </div>
            <div className="practice-group">
              <span className="practice-label">Luyện phần này ({SECTION_LABELS[section]}):</span>
              <button type="button" className="btn btn-primary btn-sm"
                onClick={() => requireLogin('Đăng nhập để luyện flashcard và lưu tiến trình.', () => goFlashcard('SECTION', sectionId))}>
                🃏 Flashcard
              </button>
              <button type="button" className="btn btn-outline btn-sm"
                onClick={() => requireLogin('Đăng nhập để làm quiz và lưu tiến trình.', () => goQuiz('SECTION', sectionId))}>
                ✏️ Quiz
              </button>
              <button type="button" className="btn btn-outline btn-sm"
                onClick={() => requireLogin('Đăng nhập để luyện gõ phiên âm và lưu tiến trình.', () => goTyping('SECTION', sectionId))}>
                ⌨️ Gõ phiên âm
              </button>
            </div>
          </div>

          <div className="tabs">
            {SECTION_TABS.map((t) => (
              <button key={t.value} type="button" className={`tab${section === t.value ? ' active' : ''}`}
                onClick={() => setSection(t.value)}>
                {t.label}
              </button>
            ))}
          </div>

          <div className="toolbar">
            <span className="muted">{words.length} từ · đã chọn {selectedWords.length}</span>
            {selectedWords.length > 0 && (
              <button type="button" className="btn btn-primary btn-sm"
                onClick={() => requireLogin('Đăng nhập để thêm từ vào bộ thẻ của bạn.', () => setAddSetOpen(true))}>
                ＋ Thêm {selectedWords.length} từ vào bộ thẻ
              </button>
            )}
            <div className="legend">
              {Object.entries(STATUS_LABELS).map(([k, v]) => (
                <span key={k} className="legend-item"><span className={`status-dot ${STATUS_COLORS[k]}`} /> {v}</span>
              ))}
            </div>
          </div>

          <div className="word-list">
            {words.map((w) => (
              <WordCard
                key={w.id}
                word={w}
                selectable
                selected={!!selected[w.id]}
                onToggleSelect={toggleSelect}
                onAddToSet={(word) => requireLogin('Đăng nhập để thêm từ vào bộ thẻ của bạn.', () => {
                  setSelected({ [word.id]: word });
                  setAddSetOpen(true);
                })}
              />
            ))}
          </div>
          {words.length === 0 && <p className="muted">Phần này chưa có từ vựng.</p>}
        </>
      )}
      <LoginPrompt open={!!loginPrompt} onClose={() => setLoginPrompt('')} message={loginPrompt} />
      <AddToSetModal open={addSetOpen} onClose={() => setAddSetOpen(false)} words={selectedWords} />
    </div>
  );
}
