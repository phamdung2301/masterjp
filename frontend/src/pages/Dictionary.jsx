import { useEffect, useRef, useState } from 'react';
import { searchWords } from '../api/study.js';
import { getErrorMessage } from '../api/client.js';
import { useAuth } from '../context/AuthContext.jsx';
import WordCard from '../components/WordCard.jsx';
import LoginPrompt from '../components/LoginPrompt.jsx';
import AddToSetModal from '../components/AddToSetModal.jsx';
import EmptyState from '../components/EmptyState.jsx';
import { SECTION_LABELS } from '../utils.js';
import './pages.css';

// US-004 (F-012): tìm kiếm từ điển theo kana/kanji/romaji/nghĩa Việt.
// Guest xem được; nút "+ Bộ thẻ" → mời đăng nhập (US-016).
export default function Dictionary() {
  const { isLoggedIn } = useAuth();
  const [q, setQ] = useState('');
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [searched, setSearched] = useState(false);
  const [loginPrompt, setLoginPrompt] = useState('');
  const [addWords, setAddWords] = useState([]);
  const [addSetOpen, setAddSetOpen] = useState(false);
  const timer = useRef(null);

  useEffect(() => () => { if (timer.current) clearTimeout(timer.current); }, []);

  const doSearch = async (keyword) => {
    const kw = keyword.trim();
    if (!kw) { setResults([]); setSearched(false); return; }
    setLoading(true);
    setError('');
    try {
      const data = await searchWords(kw);
      setResults(Array.isArray(data) ? data : data.results || []);
      setSearched(true);
    } catch (e) {
      setError(getErrorMessage(e, 'Không tìm được, vui lòng thử lại.'));
    } finally {
      setLoading(false);
    }
  };

  const handleChange = (v) => {
    setQ(v);
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(() => doSearch(v), 400);
  };

  const handleAddToSet = (word) => {
    if (!isLoggedIn) {
      setLoginPrompt('Đăng nhập để thêm từ vào bộ thẻ của bạn.');
      return;
    }
    setAddWords([word]);
    setAddSetOpen(true);
  };

  return (
    <div className="page">
      <h1>Từ điển</h1>
      <p className="muted">Tìm theo kana, kanji, romaji hoặc nghĩa tiếng Việt (gõ không dấu vẫn được).</p>
      <div className="search-box">
        <input
          type="text"
          placeholder="Ví dụ: たべる, 食べる, taberu, ăn…"
          value={q}
          onChange={(e) => handleChange(e.target.value)}
          autoFocus
        />
        {loading && <span className="search-loading">Đang tìm…</span>}
      </div>
      {error && <div className="alert alert-error">{error}</div>}
      {searched && !loading && results.length > 0 && (
        <p className="muted">Tìm thấy {results.length} từ:</p>
      )}
      {searched && !loading && results.length === 0 && !error && (
        <EmptyState
          badge="辞"
          title="Tanu-chan tìm mãi không thấy…"
          message={`Không có từ nào khớp với "${q}". Thử gõ kana, romaji hoặc nghĩa tiếng Việt khác nhé — gõ không dấu vẫn được.`}
        />
      )}
      <div className="word-list">
        {results.map((w) => (
          <WordCard
            key={w.id}
            word={{ ...w, lessonLabel: w.lessonNo ? `Bài ${w.lessonNo}` : (w.lessonLabel || '') }}
            showLesson
            onAddToSet={handleAddToSet}
          />
        ))}
      </div>
      <LoginPrompt open={!!loginPrompt} onClose={() => setLoginPrompt('')} message={loginPrompt} />
      <AddToSetModal open={addSetOpen} onClose={() => setAddSetOpen(false)} words={addWords} />
    </div>
  );
}
