import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { fetchKanji } from '../api/kanji.js';
import { getErrorMessage } from '../api/client.js';
import './pages.css';

// F-B+: chọn kanji trước khi luyện flashcard.
// - Chọn nhiều bài (checkbox grid): tick bài = bulk chọn hết kanji có lessonNos
//   chứa bài đó; sau đó có thể bỏ tick từng chữ.
// - Tick từng chữ riêng lẻ (checkbox trên thẻ; bấm vào chữ vẫn sang chi tiết).
// - Tập chọn cuối = Set theo `character` (extra: tick tay, dropped: bỏ tick tay).
// - Nút luyện: N > 0 → lưu full object kanji vào sessionStorage `mjp_kanji_pick`
//   rồi sang /luyen-tap/kanji?pick=1. N = 0 → confirm trước khi luyện toàn bộ
//   (tránh bấm nhầm 988 chữ như trước).
const KANJI_PICK_KEY = 'mjp_kanji_pick';
const LESSON_OPTIONS = Array.from({ length: 50 }, (_, i) => i + 1);

export default function Kanji() {
  const navigate = useNavigate();
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [jlpt, setJlpt] = useState('');
  const [search, setSearch] = useState('');
  const [selLessons, setSelLessons] = useState([]); // number[] — bài đã tick
  const [extra, setExtra] = useState(() => new Set()); // chữ tick tay (ngoài bài đã chọn)
  const [dropped, setDropped] = useState(() => new Set()); // chữ bỏ tick tay (dù trong bài đã chọn)

  // Fetch 1 lần toàn bộ; mọi filter (bài, cấp độ, tìm chữ) đều client-side.
  useEffect(() => {
    setLoading(true);
    setError('');
    fetchKanji({})
      .then((data) => setItems(Array.isArray(data) ? data : []))
      .catch((e) => setError(getErrorMessage(e, 'Không tải được danh sách kanji.')))
      .finally(() => setLoading(false));
  }, []);

  const byChar = useMemo(() => {
    const m = new Map();
    for (const k of items) if (k.character) m.set(k.character, k);
    return m;
  }, [items]);

  const lessonSet = useMemo(() => new Set(selLessons), [selLessons]);

  // Danh sách hiển thị: lọc cấp độ + tìm chữ (bài đã tick dùng để bulk pick,
  // không ẩn các chữ khác để vẫn tick lẻ được).
  const filtered = useMemo(() => {
    const q = search.trim();
    return items.filter((k) => {
      if (!k.character) return false;
      if (jlpt && k.jlpt !== jlpt) return false;
      if (q && !k.character.includes(q)) return false;
      return true;
    });
  }, [items, jlpt, search]);

  // Tập chữ đang được chọn (derived từ bài đã tick ± tick tay).
  const picked = useMemo(() => {
    const s = new Set();
    for (const k of items) {
      const ch = k.character;
      if (!ch) continue;
      if (extra.has(ch)) { s.add(ch); continue; }
      if (dropped.has(ch)) continue;
      if (lessonSet.size > 0 && (k.lessonNos || []).some((n) => lessonSet.has(n))) {
        s.add(ch);
      }
    }
    return s;
  }, [items, extra, dropped, lessonSet]);

  const toggleLesson = (n) => {
    setSelLessons((prev) => (prev.includes(n) ? prev.filter((x) => x !== n) : [...prev, n]));
  };

  const toggleAllLessons = () => {
    setSelLessons((prev) => (prev.length === LESSON_OPTIONS.length ? [] : [...LESSON_OPTIONS]));
  };

  const toggleChar = (ch) => {
    const k = byChar.get(ch);
    const covered = !!k && (k.lessonNos || []).some((n) => lessonSet.has(n));
    if (picked.has(ch)) {
      // Đang chọn → bỏ chọn (xóa khỏi extra; nếu đang được cover bởi bài thì cho vào dropped).
      setExtra((prev) => {
        if (!prev.has(ch)) return prev;
        const n = new Set(prev);
        n.delete(ch);
        return n;
      });
      if (covered) setDropped((prev) => new Set(prev).add(ch));
    } else {
      // Chưa chọn → chọn (xóa khỏi dropped; chỉ cho vào extra nếu không được cover).
      setDropped((prev) => {
        if (!prev.has(ch)) return prev;
        const n = new Set(prev);
        n.delete(ch);
        return n;
      });
      if (!covered) setExtra((prev) => new Set(prev).add(ch));
    }
  };

  const selectVisible = () => {
    const chars = filtered.map((k) => k.character).filter(Boolean);
    setDropped((prev) => {
      const n = new Set(prev);
      chars.forEach((ch) => n.delete(ch));
      return n;
    });
    setExtra((prev) => {
      const n = new Set(prev);
      for (const ch of chars) {
        const k = byChar.get(ch);
        const covered = !!k && (k.lessonNos || []).some((no) => lessonSet.has(no));
        if (!covered) n.add(ch);
      }
      return n;
    });
  };

  const deselectVisible = () => {
    const chars = filtered.map((k) => k.character).filter(Boolean);
    setExtra((prev) => {
      const n = new Set(prev);
      chars.forEach((ch) => n.delete(ch));
      return n;
    });
    setDropped((prev) => {
      const n = new Set(prev);
      chars.forEach((ch) => n.add(ch));
      return n;
    });
  };

  const goFlashcard = () => {
    if (picked.size > 0) {
      const arr = [...picked]
        .map((ch) => byChar.get(ch))
        .filter(Boolean)
        .map((k) => ({
          character: k.character,
          onYomi: k.onYomi,
          kunYomi: k.kunYomi,
          meaningVi: k.meaningVi,
          strokes: k.strokes,
          jlpt: k.jlpt,
        }));
      sessionStorage.setItem(KANJI_PICK_KEY, JSON.stringify(arr));
      navigate('/luyen-tap/kanji?pick=1');
      return;
    }
    if (!window.confirm(`Bạn sắp luyện toàn bộ ${items.length} chữ. Tiếp tục?`)) return;
    navigate('/luyen-tap/kanji');
  };

  return (
    <div className="page">
      <div className="kanji-head">
        <div>
          <h1>Kanji</h1>
          <p className="muted">Học riêng từng chữ kanji: âm đọc, nghĩa, số nét và các từ chứa chữ đó.</p>
        </div>
        <button type="button" className="btn btn-primary" onClick={goFlashcard}>
          🃏 Luyện flashcard{picked.size > 0 ? ` (${picked.size} chữ)` : ''}
        </button>
      </div>

      <div className="lesson-pick">
        <div className="lesson-pick-head">
          <span className="lesson-pick-title">Chọn bài để lấy kanji ({selLessons.length}/50)</span>
          <button type="button" className="btn btn-ghost btn-sm" onClick={toggleAllLessons}>
            {selLessons.length === LESSON_OPTIONS.length ? 'Bỏ chọn hết' : 'Chọn tất cả'}
          </button>
        </div>
        <div className="lesson-pick-grid">
          {LESSON_OPTIONS.map((n) => (
            <label key={n} className={`lesson-pick-item${selLessons.includes(n) ? ' selected' : ''}`}>
              <input type="checkbox" checked={selLessons.includes(n)} onChange={() => toggleLesson(n)} />
              <span>{n}</span>
            </label>
          ))}
        </div>
      </div>

      <div className="toolbar kanji-filters">
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
        <button type="button" className="btn btn-ghost btn-sm" onClick={selectVisible} disabled={filtered.length === 0}>
          Chọn tất cả
        </button>
        <button type="button" className="btn btn-ghost btn-sm" onClick={deselectVisible} disabled={picked.size === 0}>
          Bỏ chọn
        </button>
        <span className="muted">{filtered.length} chữ · đã chọn {picked.size}</span>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      {loading ? (
        <p className="muted">Đang tải…</p>
      ) : filtered.length === 0 ? (
        <p className="muted">Không tìm thấy chữ kanji nào phù hợp.</p>
      ) : (
        <div className="kanji-grid">
          {filtered.map((k) => (
            <Link
              key={k.character}
              to={`/kanji/${encodeURIComponent(k.character)}`}
              className={`kanji-card${picked.has(k.character) ? ' picked' : ''}`}
            >
              <input
                type="checkbox"
                className="kanji-card-check"
                checked={picked.has(k.character)}
                onClick={(e) => e.stopPropagation()}
                onChange={(e) => { e.stopPropagation(); toggleChar(k.character); }}
                aria-label={`Chọn chữ ${k.character}`}
              />
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
