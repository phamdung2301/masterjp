import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { fetchKanji } from '../api/kanji.js';
import { getErrorMessage } from '../api/client.js';
import './pages.css';

// F-B: trang kanji — lưới chữ lớn, lọc theo bài (1–50) + cấp độ N5/N4 + tìm theo chữ.
// Contract: GET /api/kanji?lessonNo=&jlpt= — luôn sắp theo strokes tăng dần.
const LESSON_OPTIONS = Array.from({ length: 50 }, (_, i) => i + 1);

export default function Kanji() {
  const navigate = useNavigate();
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [lessonNo, setLessonNo] = useState('');
  const [jlpt, setJlpt] = useState('');
  const [search, setSearch] = useState('');

  useEffect(() => {
    setLoading(true);
    setError('');
    fetchKanji({ lessonNo: lessonNo || undefined, jlpt: jlpt || undefined })
      .then((data) => setItems(Array.isArray(data) ? data : []))
      .catch((e) => setError(getErrorMessage(e, 'Không tải được danh sách kanji.')))
      .finally(() => setLoading(false));
  }, [lessonNo, jlpt]);

  const filtered = useMemo(() => {
    const q = search.trim();
    if (!q) return items;
    return items.filter((k) => k.character && k.character.includes(q));
  }, [items, search]);

  const goFlashcard = () => {
    const params = new URLSearchParams();
    if (lessonNo) params.set('lessonNo', lessonNo);
    if (jlpt) params.set('jlpt', jlpt);
    navigate(`/luyen-tap/kanji?${params.toString()}`);
  };

  return (
    <div className="page">
      <div className="kanji-head">
        <div>
          <h1>Kanji</h1>
          <p className="muted">Học riêng từng chữ kanji: âm đọc, nghĩa, số nét và các từ chứa chữ đó.</p>
        </div>
        <button type="button" className="btn btn-primary" onClick={goFlashcard}>
          🃏 Luyện flashcard{filtered.length > 0 ? ` (${filtered.length} chữ)` : ''}
        </button>
      </div>

      <div className="toolbar kanji-filters">
        <select className="select-inline" value={lessonNo} onChange={(e) => setLessonNo(e.target.value)} aria-label="Lọc theo bài">
          <option value="">Tất cả các bài</option>
          {LESSON_OPTIONS.map((n) => <option key={n} value={n}>Bài {n}</option>)}
        </select>
        <select className="select-inline" value={jlpt} onChange={(e) => setJlpt(e.target.value)} aria-label="Lọc theo cấp độ">
          <option value="">Cấp độ N5–N4</option>
          <option value="N5">N5</option>
          <option value="N4">N4</option>
        </select>
        <input
          type="text"
          className="kanji-search"
          placeholder="Tìm theo chữ, vd. 私"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <span className="muted">{filtered.length} chữ</span>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      {loading ? (
        <p className="muted">Đang tải…</p>
      ) : filtered.length === 0 ? (
        <p className="muted">Không tìm thấy chữ kanji nào phù hợp.</p>
      ) : (
        <div className="kanji-grid">
          {filtered.map((k) => (
            <Link key={k.character} to={`/kanji/${encodeURIComponent(k.character)}`} className="kanji-card">
              <div className="kanji-char">{k.character}</div>
              <div className="kanji-meaning">{k.meaningVi}</div>
              <div className="kanji-meta">
                <span className="badge">{k.jlpt}</span>
                <span className="muted">{k.strokes} nét</span>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
