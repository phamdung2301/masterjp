import { useEffect, useMemo, useRef, useState } from 'react';
import { fetchLessons } from '../api/lessons.js';
import { fetchGrammar, searchGrammar } from '../api/grammar.js';
import { getErrorMessage } from '../api/client.js';
import GrammarPointCard from '../components/GrammarPointCard.jsx';
import GrammarRelatedModal from '../components/GrammarRelatedModal.jsx';
import './pages.css';

// Trang Ngữ pháp (/ngu-phap) — public, guest xem được.
// - Ô tìm kiếm đầu trang: debounce 400ms → searchGrammar(q).
//   Có query → thay accordion bằng danh sách kết quả (mỗi card có nhãn "Bài N").
// - Accordion 50 bài (pattern như QuickPractice): mở bài nào thì fetch
//   fetchGrammar({ lessonNo }) lazy + cache theo bài.
export default function Grammar() {
  const [lessons, setLessons] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [expanded, setExpanded] = useState({}); // lessonNo → true
  const [lessonData, setLessonData] = useState({}); // lessonNo → { loading, items, error }

  const [q, setQ] = useState('');
  const [results, setResults] = useState([]);
  const [searching, setSearching] = useState(false);
  const [searchError, setSearchError] = useState('');
  const [searched, setSearched] = useState(false);
  const timer = useRef(null);

  const [relatedId, setRelatedId] = useState(null);

  useEffect(() => () => { if (timer.current) clearTimeout(timer.current); }, []);

  // Load danh sách 50 bài (API lessons yêu cầu level → gọi cả N5 và N4).
  useEffect(() => {
    Promise.all([fetchLessons('N5'), fetchLessons('N4')])
      .then(([n5, n4]) =>
        setLessons([...(n5 || []), ...(n4 || [])].sort((a, b) => (a.lessonNo ?? 0) - (b.lessonNo ?? 0))))
      .catch((e) => setError(getErrorMessage(e, 'Không tải được danh sách bài học.')))
      .finally(() => setLoading(false));
  }, []);

  // Fetch ngữ pháp của 1 bài khi mở accordion lần đầu (cache lại các lần sau).
  const ensureLessonGrammar = async (lessonNo) => {
    const cached = lessonData[lessonNo];
    if (cached && (cached.items || cached.done)) return cached;
    setLessonData((prev) => ({
      ...prev,
      [lessonNo]: { loading: true, items: [], error: '' },
    }));
    try {
      const items = await fetchGrammar({ lessonNo });
      const data = { loading: false, done: true, items: items || [], error: '' };
      setLessonData((prev) => ({ ...prev, [lessonNo]: data }));
      return data;
    } catch (e) {
      setLessonData((prev) => ({
        ...prev,
        [lessonNo]: { loading: false, done: true, items: [], error: getErrorMessage(e, 'Không tải được ngữ pháp bài này.') },
      }));
      throw e;
    }
  };

  const toggleExpand = (lessonNo) => {
    const willOpen = !expanded[lessonNo];
    setExpanded((prev) => {
      const next = { ...prev };
      if (willOpen) next[lessonNo] = true;
      else delete next[lessonNo];
      return next;
    });
    if (willOpen) ensureLessonGrammar(lessonNo).catch(() => {});
  };

  // Tìm kiếm debounce 400ms (pattern như Dictionary).
  const doSearch = async (keyword) => {
    const kw = keyword.trim();
    if (!kw) { setResults([]); setSearched(false); setSearchError(''); return; }
    setSearching(true);
    setSearchError('');
    try {
      const data = await searchGrammar(kw);
      setResults(Array.isArray(data) ? data : []);
      setSearched(true);
    } catch (e) {
      setSearchError(getErrorMessage(e, 'Không tìm được, vui lòng thử lại.'));
    } finally {
      setSearching(false);
    }
  };

  const handleChange = (v) => {
    setQ(v);
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(() => doSearch(v), 400);
  };

  // Map id → title phục vụ chip compareWith (từ kết quả search + các bài đã mở).
  const relatedTitles = useMemo(() => {
    const map = {};
    const addItems = (items) => {
      for (const p of items || []) if (p?.id && p?.title) map[p.id] = p.title;
    };
    addItems(results);
    for (const data of Object.values(lessonData)) addItems(data.items);
    return map;
  }, [results, lessonData]);

  const groups = useMemo(() => {
    const n5 = lessons.filter((l) => l.level === 'N5' || l.lessonNo <= 25);
    const n4 = lessons.filter((l) => !(l.level === 'N5' || l.lessonNo <= 25));
    return [
      { level: 'N5', label: 'N5 · Bài 1–25', lessons: n5 },
      { level: 'N4', label: 'N4 · Bài 26–50', lessons: n4 },
    ];
  }, [lessons]);

  const showResults = searched || searching;

  if (loading) return <div className="page"><p className="muted">Đang tải…</p></div>;

  return (
    <div className="page grammar-page">
      <h1>📖 Ngữ pháp</h1>
      <p className="muted">Tất cả điểm ngữ pháp theo từng bài — bấm vào điểm nào để xem giải thích, ví dụ và lưu ý.</p>

      <div className="search-box">
        <input
          type="text"
          placeholder="Tìm ngữ pháp: 〜てはならない, かもしれない, phải làm gì…"
          value={q}
          onChange={(e) => handleChange(e.target.value)}
        />
        {searching && <span className="search-loading">Đang tìm…</span>}
      </div>
      {searchError && <div className="alert alert-error">{searchError}</div>}

      {showResults ? (
        <>
          {searched && !searching && (
            <p className="muted">
              {results.length > 0
                ? `Tìm thấy ${results.length} điểm ngữ pháp:`
                : 'Không tìm thấy điểm ngữ pháp nào. Thử từ khóa khác nhé.'}
            </p>
          )}
          <div className="grammar-list">
            {results.map((p) => (
              <GrammarPointCard
                key={p.id}
                point={p}
                showLesson
                relatedTitles={relatedTitles}
                onRelated={(id) => setRelatedId(id)}
              />
            ))}
          </div>
        </>
      ) : (
        <>
          {error && <div className="alert alert-error">{error}</div>}
          {groups.map((g) => g.lessons.length > 0 && (
            <div key={g.level} className="qp-level-group">
              <div className="qp-level-title">{g.label}</div>
              {g.lessons.map((lesson) => {
                const no = lesson.lessonNo;
                const open = !!expanded[no];
                const data = lessonData[no];
                const count = data?.items?.length;
                return (
                  <div key={lesson.id} className={`qp-acc${open ? ' open' : ''}`}>
                    <button type="button" className="qp-acc-head" onClick={() => toggleExpand(no)} aria-expanded={open}>
                      <span className="qp-acc-chevron">{open ? '▾' : '▸'}</span>
                      <span className="qp-acc-title">Bài {no} — {lesson.titleVi}</span>
                      {count > 0 && <span className="qp-acc-sel">{count} điểm ngữ pháp</span>}
                      {data?.loading && <span className="muted">đang tải…</span>}
                    </button>
                    {open && (
                      <div className="qp-acc-body">
                        {data?.loading && <p className="muted">Đang tải ngữ pháp bài {no}…</p>}
                        {data?.error && <div className="alert alert-error">{data.error}</div>}
                        {data && !data.loading && !data.error && data.items.length === 0 && (
                          <p className="muted">Bài này chưa có dữ liệu ngữ pháp.</p>
                        )}
                        <div className="grammar-list">
                          {(data?.items || []).map((p) => (
                            <GrammarPointCard
                              key={p.id}
                              point={p}
                              relatedTitles={relatedTitles}
                              onRelated={(id) => setRelatedId(id)}
                            />
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          ))}
        </>
      )}

      <GrammarRelatedModal
        grammarId={relatedId}
        relatedTitles={relatedTitles}
        onClose={() => setRelatedId(null)}
        onRelated={(id) => setRelatedId(id)}
      />
    </div>
  );
}
