import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { fetchKanji, fetchKanjiTopics, generateKanjiQuiz } from '../api/kanji.js';
import { fetchLessons, fetchLessonWords } from '../api/lessons.js';
import { getErrorMessage } from '../api/client.js';
import { useAuth } from '../context/AuthContext.jsx';
import WordCard from '../components/WordCard.jsx';
import AddToSetModal from '../components/AddToSetModal.jsx';
import LoginPrompt from '../components/LoginPrompt.jsx';
import { CardIcon, PencilIcon } from '../components/icons.jsx';
import { QUIZ_MODES, clusterLabel } from '../utils.js';
import './pages.css';

// Trang Kanji — mô hình như trang bài học:
// - Chọn nhiều bài (grid checkbox): tick bài = bulk chọn hết kanji có lessonNos
//   chứa bài đó. Chọn chủ đề (grid checkbox 17 chủ đề): tick chủ đề = bulk
//   chọn hết kanji có topic đó. Sau đó có thể bỏ tick từng chữ.
// - Tick từng chữ riêng lẻ (checkbox trên thẻ; bấm vào chữ vẫn sang chi tiết).
// - Tập chữ chọn cuối = Set theo `character`
//   (lesson/topic coverage ∪ tick tay − bỏ tick tay).
// - Danh sách từ vựng của các bài đã chọn: nhóm theo cụm chủ đề (như
//   LessonDetail), tick từng từ / tick cả cụm.
// - Thanh luyện tập sticky thống nhất, 2 nhóm:
//   chữ kanji → flashcard / quiz kanji / gõ cách đọc (POST /api/kanji/quiz);
//   từ vựng → flashcard / quiz / gõ phiên âm / thêm vào bộ thẻ
//   (pattern ?quick=1 + sessionStorage như LessonDetail).
const KANJI_PICK_KEY = 'mjp_kanji_pick';
const KANJI_QUIZ_KEY = 'mjp_kanji_quiz';
const WORD_QUICK_KEY = 'mjp_quick_quiz';
const LESSON_OPTIONS = Array.from({ length: 50 }, (_, i) => i + 1);
const MIN_MCQ_CHARS = 4; // trắc nghiệm cần ≥ 4 chữ để đủ đáp án nhiễu
const FLASHCARD_DIRS = [
  { value: 'JP_VI', label: 'Nhật → Việt' },
  { value: 'VI_JP', label: 'Việt → Nhật' },
];
const QUIZ_URL_MODE = { JP_VI: 'jp_vi', VI_JP: 'vi_jp', LISTENING: 'listening' };
const KANJI_QUIZ_MODES = [
  { value: 'KANJI_MEANING', label: 'Chữ → Nghĩa' },
  { value: 'MEANING_KANJI', label: 'Nghĩa → Chữ' },
];

export default function Kanji() {
  const navigate = useNavigate();
  const { isLoggedIn } = useAuth();

  // --- data kanji ---
  const [items, setItems] = useState([]);
  const [topics, setTopics] = useState([]); // [{code, label, count}]
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // --- filter & pick kanji ---
  // Luồng theo user chốt: chọn CẤP ĐỘ trước (tab N5/N4) → chọn bài/chủ đề
  // → lưới CHỈ hiện kanji thuộc bài/chủ đề đã chọn của cấp độ đó (không xổ hết 988 chữ).
  const [level, setLevel] = useState('N5');
  const [search, setSearch] = useState('');
  const [selLessons, setSelLessons] = useState([]); // number[]
  const [selTopics, setSelTopics] = useState([]); // topic code[]
  const [extra, setExtra] = useState(() => new Set());
  const [dropped, setDropped] = useState(() => new Set());

  // --- từ vựng theo bài ---
  const [lessons, setLessons] = useState([]); // [{id, lessonNo, titleVi, ...}]
  const [lessonWords, setLessonWords] = useState({}); // lessonNo -> words[]
  const [wordsLoading, setWordsLoading] = useState(false);
  const [wordsError, setWordsError] = useState('');
  const [selWordClusters, setSelWordClusters] = useState({}); // "lessonNo:cluster" -> true
  const [selWords, setSelWords] = useState({}); // wordId -> word (tick tay)
  const [droppedWords, setDroppedWords] = useState(() => new Set()); // wordId bị loại tay khỏi cụm đã tick

  // --- luyện tập ---
  const [kanjiQuizMode, setKanjiQuizMode] = useState('KANJI_MEANING');
  const [wordQuizMode, setWordQuizMode] = useState('JP_VI');
  const [flashcardDir, setFlashcardDir] = useState('JP_VI');
  const [practicing, setPracticing] = useState(false);
  const [practiceError, setPracticeError] = useState('');
  const [loginPrompt, setLoginPrompt] = useState('');
  const [addSetOpen, setAddSetOpen] = useState(false);

  const requireLogin = (message, fn) => {
    if (!isLoggedIn) {
      setLoginPrompt(message);
      return;
    }
    fn();
  };

  // Fetch 1 lần: toàn bộ kanji + topics + lessons (map id theo lessonNo).
  useEffect(() => {
    setLoading(true);
    setError('');
    Promise.all([
      fetchKanji({}),
      fetchKanjiTopics().catch(() => []),
      Promise.all([fetchLessons('N5'), fetchLessons('N4')])
        .then(([n5, n4]) => [...(n5 || []), ...(n4 || [])])
        .catch(() => []),
    ])
      .then(([kanjiData, topicData, lessonData]) => {
        setItems(Array.isArray(kanjiData) ? kanjiData : []);
        setTopics(Array.isArray(topicData) ? topicData : []);
        setLessons(Array.isArray(lessonData) ? lessonData : []);
      })
      .catch((e) => setError(getErrorMessage(e, 'Không tải được danh sách kanji.')))
      .finally(() => setLoading(false));
  }, []);

  const byChar = useMemo(() => {
    const m = new Map();
    for (const k of items) if (k.character) m.set(k.character, k);
    return m;
  }, [items]);

  const lessonSet = useMemo(() => new Set(selLessons), [selLessons]);
  const topicSet = useMemo(() => new Set(selTopics), [selTopics]);
  const lessonIdByNo = useMemo(() => {
    const m = {};
    for (const l of lessons) m[l.lessonNo] = l.id;
    return m;
  }, [lessons]);

  // Fetch từ vựng cho các bài mới tick (song song, cache theo bài).
  useEffect(() => {
    const missing = selLessons.filter((n) => !(n in lessonWords) && lessonIdByNo[n]);
    if (missing.length === 0) return;
    setWordsLoading(true);
    setWordsError('');
    Promise.all(
      missing.map((n) =>
        fetchLessonWords(lessonIdByNo[n])
          .then((ws) => [n, Array.isArray(ws) ? ws : []])
          .catch(() => [n, []])
      )
    )
      .then((pairs) => {
        setLessonWords((prev) => ({ ...prev, ...Object.fromEntries(pairs) }));
      })
      .catch((e) => setWordsError(getErrorMessage(e, 'Không tải được từ vựng.')))
      .finally(() => setWordsLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selLessons, lessonIdByNo]);

  // Đổi cấp độ = bắt đầu lại sạch: bỏ chọn bài/chủ đề/chữ/từ của cấp độ cũ
  // (mỗi chữ kanji thuộc đúng 1 cấp độ nên giữ chọn cũ = trạng thái ẩn gây rối).
  const switchLevel = (lv) => {
    if (lv === level) return;
    setLevel(lv);
    setSelLessons([]);
    setSelTopics([]);
    setExtra(new Set());
    setDropped(new Set());
    setSelWordClusters({});
    setSelWords({});
    setDroppedWords(new Set());
    setSearch('');
  };

  // Lưới kanji HIỂN THỊ: theo cấp độ đang chọn.
  // - Đang gõ tìm kiếm: tìm trong cấp độ (không cần chọn bài trước).
  // - Chưa chọn bài/chủ đề nào: TRỐNG (UI hiện hướng dẫn) — không xổ hết.
  // - Đã chọn: chỉ hiện kanji thuộc (bài đã chọn ∪ chủ đề đã chọn).
  const filtered = useMemo(() => {
    const q = search.trim();
    return items.filter((k) => {
      if (!k.character) return false;
      if (k.jlpt !== level) return false;
      if (q) return k.character.includes(q);
      if (lessonSet.size === 0 && topicSet.size === 0) return false;
      const inLesson = (k.lessonNos || []).some((n) => lessonSet.has(n));
      const inTopic = k.topic && topicSet.has(k.topic);
      return inLesson || inTopic;
    });
  }, [items, level, search, lessonSet, topicSet]);

  // Tập chữ kanji đang chọn: coverage(bài ∪ chủ đề) ∪ tick tay − bỏ tick tay.
  const picked = useMemo(() => {
    const s = new Set();
    for (const k of items) {
      const ch = k.character;
      if (!ch) continue;
      if (extra.has(ch)) { s.add(ch); continue; }
      if (dropped.has(ch)) continue;
      const inLesson = lessonSet.size > 0 && (k.lessonNos || []).some((n) => lessonSet.has(n));
      const inTopic = topicSet.size > 0 && k.topic && topicSet.has(k.topic);
      if (inLesson || inTopic) s.add(ch);
    }
    return s;
  }, [items, extra, dropped, lessonSet, topicSet]);

  const isCovered = (k) => {
    if (!k) return false;
    return ((k.lessonNos || []).some((n) => lessonSet.has(n)))
      || (k.topic && topicSet.has(k.topic));
  };

  const levelLessonNos = useMemo(
    () => LESSON_OPTIONS.filter((n) => (level === 'N5' ? n <= 25 : n > 25)),
    [level]
  );

  const toggleLesson = (n) => {
    setSelLessons((prev) => (prev.includes(n) ? prev.filter((x) => x !== n) : [...prev, n]));
  };

  const toggleAllLessons = () => {
    setSelLessons((prev) => (prev.length === levelLessonNos.length ? [] : [...levelLessonNos]));
  };

  const toggleTopic = (code) => {
    setSelTopics((prev) => (prev.includes(code) ? prev.filter((x) => x !== code) : [...prev, code]));
  };

  const toggleAllTopics = () => {
    setSelTopics((prev) => (prev.length === topics.length && topics.length > 0 ? [] : topics.map((t) => t.code)));
  };

  const toggleChar = (ch) => {
    const covered = isCovered(byChar.get(ch));
    if (picked.has(ch)) {
      setExtra((prev) => {
        if (!prev.has(ch)) return prev;
        const n = new Set(prev);
        n.delete(ch);
        return n;
      });
      if (covered) setDropped((prev) => new Set(prev).add(ch));
    } else {
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
      for (const ch of chars) if (!isCovered(byChar.get(ch))) n.add(ch);
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

  // --- từ vựng: gom theo cụm, tick cụm / tick từ ---
  const toggleWordCluster = (lessonNo, cluster) => {
    const key = `${lessonNo}:${cluster}`;
    setSelWordClusters((prev) => {
      const next = { ...prev };
      if (next[key]) delete next[key];
      else next[key] = true;
      return next;
    });
  };

  const toggleWord = (word, clusterKey) => {
    const clusterOn = !!selWordClusters[clusterKey];
    const manual = !!selWords[word.id];
    if (droppedWords.has(word.id)) {
      // Đang bị loại tay → click để chọn lại.
      setDroppedWords((prev) => {
        const n = new Set(prev);
        n.delete(word.id);
        return n;
      });
      return;
    }
    if (clusterOn && !manual) {
      // Đang chọn theo cụm → click để loại riêng từ này.
      setDroppedWords((prev) => new Set(prev).add(word.id));
      return;
    }
    setSelWords((prev) => {
      const next = { ...prev };
      if (next[word.id]) delete next[word.id];
      else next[word.id] = word;
      return next;
    });
  };

  const isWordPicked = (w, clusterKey) => {
    if (droppedWords.has(w.id)) return false;
    return !!selWords[w.id] || !!selWordClusters[clusterKey];
  };

  const pickedWords = useMemo(() => {
    const byId = {};
    for (const [id, w] of Object.entries(selWords)) {
      if (!droppedWords.has(id)) byId[id] = w;
    }
    for (const key of Object.keys(selWordClusters)) {
      const sep = key.indexOf(':');
      const no = Number(key.slice(0, sep));
      const cluster = key.slice(sep + 1);
      for (const w of lessonWords[no] || []) {
        if ((w.cluster || 'khac') === cluster && !droppedWords.has(w.id)) byId[w.id] = w;
      }
    }
    return Object.values(byId);
  }, [selWords, selWordClusters, lessonWords, droppedWords]);

  // --- luyện tập kanji ---
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
      navigate(`/kanji/quiz?mode=${mode}`);
    } catch (e) {
      setPracticeError(getErrorMessage(e, 'Không tạo được quiz kanji.'));
    } finally {
      setPracticing(false);
    }
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
      navigate('/kanji/the?pick=1');
      return;
    }
    if (!window.confirm(`Bạn sắp luyện toàn bộ ${items.length} chữ. Tiếp tục?`)) return;
    navigate('/kanji/the');
  };

  // --- luyện tập từ vựng (pattern ?quick=1 + sessionStorage như LessonDetail) ---
  const startWordPractice = (target) => {
    const words = pickedWords;
    if (words.length === 0) return;
    const wordIds = words.map((w) => w.id);
    const returnTo = '/kanji';
    if (target === 'quiz') {
      sessionStorage.setItem(WORD_QUICK_KEY, JSON.stringify({ wordIds, words, mode: wordQuizMode, returnTo }));
      navigate(`/luyen-nhanh/quiz?quick=1&mode=${QUIZ_URL_MODE[wordQuizMode] || 'jp_vi'}`);
    } else if (target === 'typing') {
      sessionStorage.setItem(WORD_QUICK_KEY, JSON.stringify({ wordIds, words, mode: 'TYPING', returnTo }));
      navigate('/luyen-nhanh/go?quick=1');
    } else {
      sessionStorage.setItem(WORD_QUICK_KEY, JSON.stringify({ wordIds, words, mode: 'FLASHCARD', returnTo }));
      navigate(`/luyen-nhanh/the?quick=1&direction=${flashcardDir.toLowerCase()}`);
    }
  };

  const kanjiPracticeHint = picked.size === 0
    ? 'Chọn chữ kanji ở lưới bên dưới để luyện.'
    : picked.size < MIN_MCQ_CHARS
      ? `Trắc nghiệm cần ít nhất ${MIN_MCQ_CHARS} chữ (để đủ đáp án lựa chọn); gõ cách đọc đã sẵn sàng.`
      : '';

  const lessonBtn = (n) => (
    <label key={n} className={`lesson-pick-item${selLessons.includes(n) ? ' selected' : ''}`}>
      <input type="checkbox" checked={selLessons.includes(n)} onChange={() => toggleLesson(n)} />
      <span>{n}</span>
    </label>
  );

  const showPracticeBar = picked.size > 0 || pickedWords.length > 0;

  return (
    <div className="page kanji-page">
      <div className="kanji-head">
        <div>
          <h1>Kanji</h1>
          <p className="muted">Học riêng từng chữ kanji: âm đọc, nghĩa, số nét và các từ chứa chữ đó.</p>
        </div>
      </div>

      <div className="tabs level-tabs" role="tablist" aria-label="Chọn cấp độ">
        {['N5', 'N4'].map((lv) => (
          <button
            key={lv}
            type="button"
            role="tab"
            aria-selected={level === lv}
            className={`tab${level === lv ? ' active' : ''}`}
            onClick={() => switchLevel(lv)}
          >
            {lv === 'N5' ? 'N5 — Bài 1–25' : 'N4 — Bài 26–50'}
          </button>
        ))}
      </div>

      <div className="lesson-pick">
        <div className="lesson-pick-head">
          <span className="lesson-pick-title">Chọn bài để xem và chọn toàn bộ kanji của bài ({selLessons.length}/{levelLessonNos.length})</span>
          <button type="button" className="btn btn-ghost btn-sm" onClick={toggleAllLessons}>
            {selLessons.length === levelLessonNos.length ? 'Bỏ chọn hết' : 'Chọn tất cả'}
          </button>
        </div>
        <div className="lesson-pick-grid">
          {levelLessonNos.map(lessonBtn)}
        </div>
      </div>

      <div className="topic-pick">
        <div className="lesson-pick-head">
          <span className="lesson-pick-title">Chọn chủ đề để chọn kanji theo chủ đề ({selTopics.length}/{topics.length})</span>
          {topics.length > 0 && (
            <button type="button" className="btn btn-ghost btn-sm" onClick={toggleAllTopics}>
              {selTopics.length === topics.length ? 'Bỏ chọn hết' : 'Chọn tất cả'}
            </button>
          )}
        </div>
        {topics.length === 0 ? (
          <p className="muted">Chưa tải được danh sách chủ đề.</p>
        ) : (
          <div className="topic-pick-grid">
            {topics.map((t) => (
              <label key={t.code} className={`topic-pick-item${selTopics.includes(t.code) ? ' selected' : ''}`}>
                <input type="checkbox" checked={selTopics.includes(t.code)} onChange={() => toggleTopic(t.code)} />
                <span className="topic-pick-label">{t.label}</span>
                <span className="topic-pick-count muted">{t.count}</span>
              </label>
            ))}
          </div>
        )}
      </div>

      <div className="toolbar kanji-filters">
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

      {error && <div className="alert alert-error">{error}</div>}

      {loading ? (
        <p className="muted">Đang tải…</p>
      ) : filtered.length === 0 ? (
        selLessons.length === 0 && selTopics.length === 0 && !search.trim() ? (
          <div className="kanji-empty-hint">
            <p>Hãy <strong>chọn bài</strong> ở trên (hoặc chọn một chủ đề) để xem kanji của {level}.</p>
            <p className="muted">Hoặc gõ chữ kanji vào ô tìm kiếm để tra nhanh.</p>
          </div>
        ) : (
          <p className="muted">Không tìm thấy chữ kanji nào phù hợp.</p>
        )
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

      {/* Từ vựng của các bài đã chọn — nhóm theo cụm, tick từng từ / từng cụm */}
      {selLessons.length > 0 && (
        <div className="kanji-vocab">
          <h2>Từ vựng các bài đã chọn</h2>
          {wordsError && <div className="alert alert-error">{wordsError}</div>}
          {wordsLoading && <p className="muted">Đang tải từ vựng…</p>}
          {selLessons.map((n) => {
            const ws = lessonWords[n] || [];
            const lesson = lessons.find((l) => l.lessonNo === n);
            const byCluster = {};
            for (const w of ws) {
              const c = w.cluster || 'khac';
              (byCluster[c] = byCluster[c] || []).push(w);
            }
            const clusterCodes = Object.keys(byCluster);
            return (
              <div key={n} className="kanji-vocab-lesson">
                <h3 className="kanji-vocab-title">
                  Bài {n}{lesson?.titleVi ? ` — ${lesson.titleVi}` : ''}
                  <span className="muted"> · {ws.length} từ</span>
                </h3>
                {ws.length === 0 ? (
                  <p className="muted">{n in lessonWords ? 'Bài này chưa có từ vựng.' : 'Đang tải…'}</p>
                ) : (
                  <>
                    <div className="cluster-grid">
                      {clusterCodes.map((c) => (
                        <label
                          key={c}
                          className={`cluster-item${selWordClusters[`${n}:${c}`] ? ' selected' : ''}`}
                        >
                          <input
                            type="checkbox"
                            checked={!!selWordClusters[`${n}:${c}`]}
                            onChange={() => toggleWordCluster(n, c)}
                          />
                          <span className="cluster-name">{clusterLabel(c)}</span>
                          <span className="cluster-count">{byCluster[c].length} từ</span>
                        </label>
                      ))}
                    </div>
                    {clusterCodes.map((c) => (
                      <div key={c} className="cluster-section">
                        <h4 className="cluster-section-title">
                          {clusterLabel(c)}
                          <span className="muted"> · {byCluster[c].length} từ</span>
                        </h4>
                        <div className="word-list">
                          {byCluster[c].map((w) => (
                            <WordCard
                              key={w.id}
                              word={w}
                              selectable
                              selected={isWordPicked(w, `${n}:${c}`)}
                              onToggleSelect={() => toggleWord(w, `${n}:${c}`)}
                            />
                          ))}
                        </div>
                      </div>
                    ))}
                  </>
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* Thanh luyện tập sticky thống nhất */}
      {showPracticeBar && (
        <div className="selection-bar selection-bar-sticky">
          {picked.size > 0 && (
            <div className="selection-group">
              <span className="selection-count">Đã chọn <strong>{picked.size}</strong> chữ</span>
              <div className="selection-actions">
                <button type="button" className="btn btn-primary btn-sm" onClick={goFlashcard}>
                  <CardIcon size={16} /> Flashcard
                </button>
                <span className="practice-pair">
                  <button
                    type="button"
                    className="btn btn-outline btn-sm"
                    disabled={picked.size < MIN_MCQ_CHARS || practicing}
                    onClick={() => requireLogin('Đăng nhập để làm quiz kanji.', () => startKanjiQuiz(kanjiQuizMode))}
                  >
                    {practicing ? 'Đang tạo…' : 'Quiz kanji'}
                  </button>
                  <select
                    className="select-inline"
                    value={kanjiQuizMode}
                    onChange={(e) => setKanjiQuizMode(e.target.value)}
                    aria-label="Dạng quiz kanji"
                  >
                    {KANJI_QUIZ_MODES.map((m) => <option key={m.value} value={m.value}>{m.label}</option>)}
                  </select>
                </span>
                <button
                  type="button"
                  className="btn btn-outline btn-sm"
                  disabled={picked.size < 1 || practicing}
                  onClick={() => requireLogin('Đăng nhập để luyện gõ cách đọc kanji.', () => startKanjiQuiz('KANJI_TYPING'))}
                >
                  {practicing ? 'Đang tạo…' : '<PencilIcon size={16} /> Gõ cách đọc'}
                </button>
              </div>
              {kanjiPracticeHint && <div className="muted kanji-practice-hint">{kanjiPracticeHint}</div>}
            </div>
          )}
          {pickedWords.length > 0 && (
            <div className="selection-group">
              <span className="selection-count">Đã chọn <strong>{pickedWords.length}</strong> từ</span>
              <div className="selection-actions">
                <span className="practice-pair">
                  <button
                    type="button"
                    className="btn btn-primary btn-sm"
                    onClick={() => requireLogin('Đăng nhập để luyện flashcard và lưu tiến trình.', () => startWordPractice('flashcard'))}
                  >
                    <CardIcon size={16} /> Flashcard
                  </button>
                  <select
                    className="select-inline"
                    value={flashcardDir}
                    onChange={(e) => setFlashcardDir(e.target.value)}
                    aria-label="Hướng flashcard"
                  >
                    {FLASHCARD_DIRS.map((m) => <option key={m.value} value={m.value}>{m.label}</option>)}
                  </select>
                </span>
                <span className="practice-pair">
                  <button
                    type="button"
                    className="btn btn-outline btn-sm"
                    onClick={() => requireLogin('Đăng nhập để làm quiz và lưu tiến trình.', () => startWordPractice('quiz'))}
                  >
                    Quiz
                  </button>
                  <select
                    className="select-inline"
                    value={wordQuizMode}
                    onChange={(e) => setWordQuizMode(e.target.value)}
                    aria-label="Dạng quiz"
                  >
                    {QUIZ_MODES.map((m) => <option key={m.value} value={m.value}>{m.label}</option>)}
                  </select>
                </span>
                <button
                  type="button"
                  className="btn btn-outline btn-sm"
                  onClick={() => requireLogin('Đăng nhập để luyện gõ phiên âm và lưu tiến trình.', () => startWordPractice('typing'))}
                >
                  <PencilIcon size={16} /> Gõ phiên âm
                </button>
                <button
                  type="button"
                  className="btn btn-outline btn-sm"
                  onClick={() => requireLogin('Đăng nhập để thêm từ vào bộ thẻ của bạn.', () => setAddSetOpen(true))}
                >
                  ＋ Thêm vào bộ thẻ
                </button>
              </div>
            </div>
          )}
        </div>
      )}
      {practiceError && <div className="alert alert-error">{practiceError}</div>}

      <LoginPrompt open={!!loginPrompt} onClose={() => setLoginPrompt('')} message={loginPrompt} />
      <AddToSetModal open={addSetOpen} onClose={() => setAddSetOpen(false)} words={pickedWords} />
    </div>
  );
}
