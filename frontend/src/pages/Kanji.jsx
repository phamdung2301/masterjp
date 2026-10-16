import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { fetchKanji, generateKanjiQuiz } from '../api/kanji.js';
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
const KANJI_QUIZ_KEY = 'mjp_kanji_quiz';
const LESSON_OPTIONS = Array.from({ length: 50 }, (_, i) => i + 1);
const MIN_MCQ_CHARS = 4; // trắc nghiệm cần ≥ 4 chữ để đủ đáp án nhiễu (contract F-D)

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
  // R3: thanh luyện tập cho tập chữ đã chọn — quiz trắc nghiệm + gõ cách đọc
  // qua POST /api/kanji/quiz (contract F-D). v1 không lưu SM-2/progress.
  const [quizMode, setQuizMode] = useState('KANJI_MEANING'); // KANJI_MEANING | MEANING_KANJI
  const [practicing, setPracticing] = useState(false);
  const [practiceError, setPracticeError] = useState('');

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

  // R3: tạo quiz kanji cho tập chữ đã chọn rồi sang trang /luyen-tap/kanji-quiz
  // (trắc nghiệm + gõ cách đọc chung 1 trang, chấm tại chỗ).
  const startKanjiQuiz = async (mode) => {
    setPracticeError('');
    const chars = [...picked];
    if (chars.length === 0) return;
    if (mode !== 'KANJI_TYPING' && chars.length < MIN_MCQ_CHARS) return;
    setPracticing(true);
    try {
      const data = await generateKanjiQuiz({ characters: chars, mode });
      const questions = data?.questions || [];
      if (questions.length === 0) {
        throw new Error('Không tạo được câu hỏi cho tập chữ này. Hãy thử tập chữ khác.');
      }
      sessionStorage.setItem(KANJI_QUIZ_KEY, JSON.stringify({
        questions,
        mode,
        count: questions.length,
        totalChars: chars.length,
      }));
      navigate(`/luyen-tap/kanji-quiz?mode=${mode}`);
    } catch (e) {
      setPracticeError(getErrorMessage(e, 'Không tạo được quiz kanji.'));
    } finally {
      setPracticing(false);
    }
  };

  const practiceHint = picked.size === 0
    ? 'Tick chọn chữ kanji ở lưới bên dưới để luyện.'
    : picked.size < MIN_MCQ_CHARS
      ? `Trắc nghiệm cần ít nhất ${MIN_MCQ_CHARS} chữ (để đủ đáp án nhiễu); gõ cách đọc đã sẵn sàng.`
      : '';

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

  // Tick bài = chọn toàn bộ kanji của bài đó (vẫn có thể bỏ tick từng chữ bên dưới).
  const lessonBtn = (n) => (
    <label key={n} className={`lesson-pick-item${selLessons.includes(n) ? ' selected' : ''}`}>
      <input type="checkbox" checked={selLessons.includes(n)} onChange={() => toggleLesson(n)} />
      <span>{n}</span>
    </label>
  );

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
          <span className="lesson-pick-title">Tick bài để chọn toàn bộ kanji của bài ({selLessons.length}/50)</span>
          <button type="button" className="btn btn-ghost btn-sm" onClick={toggleAllLessons}>
            {selLessons.length === LESSON_OPTIONS.length ? 'Bỏ chọn hết' : 'Chọn tất cả'}
          </button>
        </div>
        <div className="lesson-pick-grid-label muted">N5 · Bài 1–25</div>
        <div className="lesson-pick-grid">
          {LESSON_OPTIONS.slice(0, 25).map(lessonBtn)}
        </div>
        <div className="lesson-pick-grid-label muted">N4 · Bài 26–50</div>
        <div className="lesson-pick-grid">
          {LESSON_OPTIONS.slice(25).map(lessonBtn)}
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
          Chọn hết đang hiện
        </button>
        <button type="button" className="btn btn-ghost btn-sm" onClick={deselectVisible} disabled={picked.size === 0}>
          Bỏ chọn đang hiện
        </button>
        <span className="muted">{filtered.length} chữ · đã chọn {picked.size}</span>
      </div>

      {/* R3: thanh luyện tập cho tập chữ đã chọn */}
      <div className="kanji-practice">
        <div className="kanji-practice-info">
          <strong>✏️ Luyện tập tập đã chọn ({picked.size} chữ)</strong>
          {practiceHint && <span className="muted"> — {practiceHint}</span>}
        </div>
        <div className="kanji-practice-actions">
          <select
            className="select-inline"
            value={quizMode}
            onChange={(e) => setQuizMode(e.target.value)}
            aria-label="Dạng quiz kanji"
          >
            <option value="KANJI_MEANING">Chữ → Nghĩa</option>
            <option value="MEANING_KANJI">Nghĩa → Chữ</option>
          </select>
          <button
            type="button"
            className="btn btn-outline"
            disabled={picked.size < MIN_MCQ_CHARS || practicing}
            onClick={() => startKanjiQuiz(quizMode)}
          >
            {practicing ? 'Đang tạo…' : '✏️ Quiz kanji'}
          </button>
          <button
            type="button"
            className="btn btn-outline"
            disabled={picked.size < 1 || practicing}
            onClick={() => startKanjiQuiz('KANJI_TYPING')}
          >
            {practicing ? 'Đang tạo…' : '⌨️ Gõ cách đọc'}
          </button>
        </div>
        {practiceError && <div className="alert alert-error">{practiceError}</div>}
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
