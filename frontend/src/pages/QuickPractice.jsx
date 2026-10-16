import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { fetchLessonClusters, fetchLessonWords, fetchLessons } from '../api/lessons.js';
import { fetchKanji } from '../api/kanji.js';
import { getErrorMessage } from '../api/client.js';
import { QUIZ_MODES, clusterLabel } from '../utils.js';
import AddToSetModal from '../components/AddToSetModal.jsx';
import './pages.css';

// R3: luyện tập nhanh dạng accordion 50 bài.
// - Mỗi bài là 1 accordion item; mở ra hiện 3 nhóm tick được:
//   cụm chủ đề (tick nhiều cụm), từ vựng (tick từng từ / "chọn tất cả"),
//   kanji (tick từng chữ). Lazy-load khi mở bài lần đầu, cache lại.
// - Từ hiệu dụng mỗi bài = (từ trong cụm đã tick ∪ từ tick tay) − từ bị loại tay.
// - Thanh sticky cuối: "Đã chọn N từ + M chữ" + 3 nút:
//   ⚡ Luyện tập nhanh (POST /api/quiz/quick qua luồng cũ, KHÔNG lưu set),
//   ＋ Thêm vào bộ thẻ (luồng cũ qua AddToSetModal),
//   🃏 Flashcard M chữ (lưu tập kanji vào `mjp_kanji_pick` → /luyen-tap/kanji?pick=1).
// - Tương thích: preset ?lessonId=&clusters= (LessonDetail dùng) và đọc được
//   format cũ của `mjp_quick_sel` { lessons, clusters, mode }.
// - Cố ý KHÔNG có nút "chọn tất cả 50 bài" 1 click (tránh fetch ồ ạt).
const QUICK_KEY = 'mjp_quick_quiz';
const QUICK_SEL_KEY = 'mjp_quick_sel'; // lưu lựa chọn để khôi phục khi quay lại trang
const KANJI_PICK_KEY = 'mjp_kanji_pick';
const MIN_WORDS = 4;
const MAX_WORDS = 100;

const clusterKey = (lessonId, cluster) => `${lessonId}:${cluster}`;
const wordCluster = (w) => w.cluster || 'khac';

export default function QuickPractice() {
  const navigate = useNavigate();
  const [params] = useSearchParams();

  const [lessons, setLessons] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [expanded, setExpanded] = useState({}); // lessonId → true (accordion đang mở)
  const [lessonData, setLessonData] = useState({}); // lessonId → { loading, clusters, words, kanji, error }
  const [tickedClusters, setTickedClusters] = useState({}); // `${lessonId}:${cluster}` → true
  const [pickedWords, setPickedWords] = useState({}); // wordId → { lessonId, cluster } (tick tay)
  const [droppedWords, setDroppedWords] = useState({}); // wordId → { lessonId, cluster } (loại tay)
  const [pickedKanji, setPickedKanji] = useState({}); // character → kanji object (tick tay)
  const [mode, setMode] = useState('JP_VI');
  const [starting, setStarting] = useState(false);
  const [startError, setStartError] = useState('');
  const [addOpen, setAddOpen] = useState(false);
  const [addWords, setAddWords] = useState([]); // [{ id }] cho AddToSetModal
  const [adding, setAdding] = useState(false);
  const [addError, setAddError] = useState('');

  // Load danh sách 50 bài (API yêu cầu level bắt buộc → gọi cả N5 và N4).
  useEffect(() => {
    Promise.all([fetchLessons('N5'), fetchLessons('N4')])
      .then(([n5, n4]) =>
        setLessons([...(n5 || []), ...(n4 || [])].sort((a, b) => (a.lessonNo ?? 0) - (b.lessonNo ?? 0))))
      .catch((e) => setError(getErrorMessage(e, 'Không tải được danh sách bài học.')))
      .finally(() => setLoading(false));
  }, []);

  // Chỉ fetch cụm (nhẹ) — dùng lúc restore để có count cho thanh sticky.
  const ensureClusters = async (lessonId) => {
    const cached = lessonData[lessonId];
    if (cached && cached.clusters) return cached.clusters;
    setLessonData((prev) => ({
      ...prev,
      [lessonId]: { loading: true, clusters: [], words: [], kanji: [], error: '' },
    }));
    const clusters = await fetchLessonClusters(lessonId);
    setLessonData((prev) => ({
      ...prev,
      [lessonId]: { ...(prev[lessonId] || {}), loading: false, clusters: clusters || [] },
    }));
    return clusters || [];
  };

  // Fetch đủ 3 nhóm khi mở accordion lần đầu (cache lại các lần sau).
  const ensureLessonData = async (lessonId) => {
    const cached = lessonData[lessonId];
    if (cached && cached.words && cached.words.length > 0) return cached;
    if (cached && cached.full) return cached;
    const lesson = lessons.find((l) => l.id === lessonId);
    setLessonData((prev) => ({
      ...prev,
      [lessonId]: { ...(prev[lessonId] || { clusters: [], words: [], kanji: [] }), loading: true, error: '' },
    }));
    try {
      const [clusters, words, kanji] = await Promise.all([
        cached?.clusters?.length ? cached.clusters : fetchLessonClusters(lessonId),
        fetchLessonWords(lessonId),
        lesson ? fetchKanji({ lessonNo: lesson.lessonNo }) : Promise.resolve([]),
      ]);
      const data = {
        loading: false,
        full: true,
        error: '',
        clusters: clusters || [],
        words: (words || []).sort((a, b) => (a.orderInLesson ?? 0) - (b.orderInLesson ?? 0)),
        kanji: kanji || [],
      };
      setLessonData((prev) => ({ ...prev, [lessonId]: data }));
      return data;
    } catch (e) {
      setLessonData((prev) => ({
        ...prev,
        [lessonId]: { ...(prev[lessonId] || { clusters: [], words: [], kanji: [] }), loading: false, error: getErrorMessage(e) },
      }));
      throw e;
    }
  };

  const toggleExpand = (lessonId) => {
    const willOpen = !expanded[lessonId];
    setExpanded((prev) => {
      const next = { ...prev };
      if (willOpen) next[lessonId] = true;
      else delete next[lessonId];
      return next;
    });
    if (willOpen) ensureLessonData(lessonId).catch(() => {});
  };

  // Lựa chọn ban đầu — ưu tiên preset từ LessonDetail (?lessonId=&clusters=),
  // nếu không có thì khôi phục lựa chọn đã lưu (đọc được cả format cũ).
  const appliedRef = useRef(false);
  const applyDoneRef = useRef(false); // apply xong (kể cả phần async) mới được ghi đè lựa chọn đã lưu
  useEffect(() => {
    if (appliedRef.current || loading || lessons.length === 0) return;
    appliedRef.current = true;
    const done = () => { applyDoneRef.current = true; };
    const lessonId = params.get('lessonId');
    const clusterParam = params.get('clusters');
    if (lessonId && lessons.some((l) => l.id === lessonId)) {
      setExpanded({ [lessonId]: true });
      ensureLessonData(lessonId).then((data) => {
        const wanted = clusterParam ? clusterParam.split(',').filter(Boolean) : null;
        const valid = (data.clusters || []).filter((c) => !wanted || wanted.includes(c.cluster));
        setTickedClusters((prev) => {
          const next = { ...prev };
          for (const c of valid) next[clusterKey(lessonId, c.cluster)] = true;
          return next;
        });
        done();
      }).catch(done);
      return;
    }
    try {
      const saved = JSON.parse(sessionStorage.getItem(QUICK_SEL_KEY) || 'null');
      const savedClusters = new Set(saved?.clusters || []);
      const lessonIds = new Set();
      for (const k of savedClusters) {
        const sep = k.lastIndexOf(':');
        if (sep > 0) lessonIds.add(k.slice(0, sep));
      }
      const picked = saved?.picked || {};
      const dropped = saved?.dropped || {};
      for (const info of Object.values(picked)) if (info?.lessonId) lessonIds.add(info.lessonId);
      for (const info of Object.values(dropped)) if (info?.lessonId) lessonIds.add(info.lessonId);
      const validIds = [...lessonIds].filter((lid) => lessons.some((l) => l.id === lid));
      if (validIds.length === 0) { done(); return; }
      if (saved?.mode) setMode(saved.mode);
      setTickedClusters(Object.fromEntries([...savedClusters].map((k) => [k, true])));
      setPickedWords(picked);
      setDroppedWords(dropped);
      const kanjiArr = Array.isArray(saved?.kanji) ? saved.kanji : [];
      setPickedKanji(Object.fromEntries(kanjiArr.filter((k) => k?.character).map((k) => [k.character, k])));
      // Mở sẵn các bài có lựa chọn để user thấy ngữ cảnh (giống cột cụm ở bản cũ).
      const expandIds = (Array.isArray(saved?.expanded) && saved.expanded.length > 0
        ? saved.expanded.filter((lid) => validIds.includes(lid))
        : validIds);
      setExpanded(Object.fromEntries(expandIds.map((lid) => [lid, true])));
      (async () => {
        // Fetch cụm cho mọi bài có tick (để thanh sticky có count); data đủ
        // (từ + kanji) chỉ load cho bài đang mở.
        for (const lid of validIds) {
          try { await ensureClusters(lid); } catch { /* bỏ qua */ }
        }
        for (const lid of expandIds) {
          try { await ensureLessonData(lid); } catch { /* bỏ qua */ }
        }
        done();
      })();
    } catch { done(); /* bỏ qua lựa chọn đã lưu nếu hỏng */ }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loading, lessons]);

  // Lưu lựa chọn mỗi khi đổi để khôi phục khi quay lại.
  // Giữ field `lessons` + `clusters` cho tương thích format cũ, thêm `picked`,
  // `dropped`, `kanji`, `expanded`, `version` cho format mới.
  useEffect(() => {
    if (!applyDoneRef.current || loading || lessons.length === 0) return;
    try {
      const lessonIds = new Set();
      for (const k of Object.keys(tickedClusters)) {
        const sep = k.lastIndexOf(':');
        if (sep > 0) lessonIds.add(k.slice(0, sep));
      }
      for (const info of Object.values(pickedWords)) if (info?.lessonId) lessonIds.add(info.lessonId);
      sessionStorage.setItem(QUICK_SEL_KEY, JSON.stringify({
        version: 2,
        lessons: [...lessonIds],
        clusters: Object.keys(tickedClusters),
        mode,
        picked: pickedWords,
        dropped: droppedWords,
        kanji: Object.values(pickedKanji),
        expanded: Object.keys(expanded),
      }));
    } catch { /* bỏ qua */ }
  }, [loading, lessons, expanded, tickedClusters, pickedWords, droppedWords, pickedKanji, mode]);

  const toggleCluster = (lessonId, cluster) => {
    const key = clusterKey(lessonId, cluster);
    setTickedClusters((prev) => {
      const next = { ...prev };
      if (next[key]) delete next[key];
      else next[key] = true;
      return next;
    });
  };

  const setAllClusters = (lessonId, on) => {
    const data = lessonData[lessonId];
    const codes = (data?.clusters || []).map((c) => c.cluster);
    if (codes.length === 0) return;
    setTickedClusters((prev) => {
      const next = { ...prev };
      for (const code of codes) {
        const key = clusterKey(lessonId, code);
        if (on) next[key] = true;
        else delete next[key];
      }
      return next;
    });
  };

  const isWordEffective = (lessonId, word) => {
    if (droppedWords[word.id]) return false;
    return !!tickedClusters[clusterKey(lessonId, wordCluster(word))] || !!pickedWords[word.id];
  };

  const toggleWord = (lessonId, word) => {
    const info = { lessonId, cluster: wordCluster(word) };
    if (isWordEffective(lessonId, word)) {
      // Đang hiệu dụng → loại tay (xóa khỏi tick tay nếu có).
      setPickedWords((prev) => {
        if (!prev[word.id]) return prev;
        const next = { ...prev };
        delete next[word.id];
        return next;
      });
      setDroppedWords((prev) => ({ ...prev, [word.id]: info }));
    } else {
      // Chưa chọn → tick tay (xóa khỏi loại tay nếu có).
      setDroppedWords((prev) => {
        if (!prev[word.id]) return prev;
        const next = { ...prev };
        delete next[word.id];
        return next;
      });
      setPickedWords((prev) => ({ ...prev, [word.id]: info }));
    }
  };

  const setAllWords = (lessonId, on) => {
    const data = lessonData[lessonId];
    const words = data?.words || [];
    if (words.length === 0) return;
    if (on) {
      setDroppedWords((prev) => {
        const next = { ...prev };
        for (const w of words) delete next[w.id];
        return next;
      });
      setPickedWords((prev) => {
        const next = { ...prev };
        for (const w of words) next[w.id] = { lessonId, cluster: wordCluster(w) };
        return next;
      });
    } else {
      setPickedWords((prev) => {
        const next = { ...prev };
        for (const w of words) delete next[w.id];
        return next;
      });
      setDroppedWords((prev) => {
        const next = { ...prev };
        for (const w of words) next[w.id] = { lessonId, cluster: wordCluster(w) };
        return next;
      });
    }
  };

  const toggleKanji = (k) => {
    if (!k?.character) return;
    setPickedKanji((prev) => {
      const next = { ...prev };
      if (next[k.character]) delete next[k.character];
      else next[k.character] = k;
      return next;
    });
  };

  const setAllKanji = (lessonId, on) => {
    const data = lessonData[lessonId];
    const chars = data?.kanji || [];
    if (chars.length === 0) return;
    setPickedKanji((prev) => {
      const next = { ...prev };
      for (const k of chars) {
        if (!k?.character) continue;
        if (on) next[k.character] = k;
        else delete next[k.character];
      }
      return next;
    });
  };

  const clearAll = () => {
    setExpanded({});
    setTickedClusters({});
    setPickedWords({});
    setDroppedWords({});
    setPickedKanji({});
  };

  const groups = useMemo(() => {
    const n5 = lessons.filter((l) => l.level === 'N5' || l.lessonNo <= 25);
    const n4 = lessons.filter((l) => !(l.level === 'N5' || l.lessonNo <= 25));
    return [
      { level: 'N5', label: 'N5 · Bài 1–25', lessons: n5 },
      { level: 'N4', label: 'N4 · Bài 26–50', lessons: n4 },
    ];
  }, [lessons]);

  // Tổng số từ hiệu dụng: bài đã load từ thì đếm chính xác; bài chưa load thì
  // ước lượng từ count cụm đã tick, điều chỉnh bằng tick/loại tay.
  const totalWords = useMemo(() => {
    let sum = 0;
    for (const lesson of lessons) {
      const lid = lesson.id;
      const data = lessonData[lid];
      const ticked = new Set(
        Object.keys(tickedClusters)
          .filter((k) => k.startsWith(`${lid}:`))
          .map((k) => k.slice(lid.length + 1))
      );
      if (data?.words?.length) {
        for (const w of data.words) {
          if (droppedWords[w.id]) continue;
          if (ticked.has(wordCluster(w)) || pickedWords[w.id]) sum++;
        }
        continue;
      }
      if (data?.clusters?.length && ticked.size > 0) {
        for (const c of data.clusters) if (ticked.has(c.cluster)) sum += c.count || 0;
      }
      for (const [id, info] of Object.entries(droppedWords)) {
        if (info?.lessonId === lid && ticked.has(info.cluster)) sum--;
      }
      for (const [id, info] of Object.entries(pickedWords)) {
        if (info?.lessonId === lid && !ticked.has(info.cluster)) sum++;
      }
    }
    return Math.max(0, sum);
  }, [lessons, lessonData, tickedClusters, pickedWords, droppedWords]);

  const totalKanji = Object.keys(pickedKanji).length;
  const hasSelection = totalWords > 0 || totalKanji > 0;
  const canStart = totalWords >= MIN_WORDS && totalWords <= MAX_WORDS && !starting;
  const canAddToSet = totalWords >= 1 && !adding && !starting;

  // Lấy wordIds thật của tập từ hiệu dụng: 1 request / bài có lựa chọn
  // (dùng cache nếu đã load). Dùng chung cho luyện nhanh và thêm vào bộ thẻ.
  const collectWordIds = async () => {
    const lids = new Set();
    for (const k of Object.keys(tickedClusters)) {
      const sep = k.lastIndexOf(':');
      if (sep > 0) lids.add(k.slice(0, sep));
    }
    for (const info of Object.values(pickedWords)) if (info?.lessonId) lids.add(info.lessonId);
    const perLesson = await Promise.all(
      [...lids].map(async (lid) => {
        const data = await ensureLessonData(lid);
        const ticked = new Set(
          Object.keys(tickedClusters)
            .filter((k) => k.startsWith(`${lid}:`))
            .map((k) => k.slice(lid.length + 1))
        );
        if (ticked.size === 0 && !Object.values(pickedWords).some((info) => info?.lessonId === lid)) return [];
        return (data.words || [])
          .filter((w) => {
            if (droppedWords[w.id]) return false;
            return ticked.has(wordCluster(w)) || pickedWords[w.id];
          })
          .map((w) => w.id);
      })
    );
    return [...new Set(perLesson.flat())];
  };

  const handleStart = async () => {
    setStartError('');
    if (totalWords < MIN_WORDS) {
      setStartError(`Cần ít nhất ${MIN_WORDS} từ để tạo quiz. Hãy tick thêm cụm, từ hoặc bài.`);
      return;
    }
    if (totalWords > MAX_WORDS) {
      setStartError(`Tối đa ${MAX_WORDS} từ mỗi lần luyện. Hãy bỏ bớt cụm hoặc từ.`);
      return;
    }
    setStarting(true);
    try {
      const wordIds = await collectWordIds();
      if (wordIds.length < MIN_WORDS) {
        throw new Error('Không đủ từ sau khi lọc. Hãy chọn thêm.');
      }
      const limited = wordIds.slice(0, MAX_WORDS);
      sessionStorage.setItem(QUICK_KEY, JSON.stringify({
        wordIds: limited,
        mode,
        totalWords: limited.length,
      }));
      const modeParam = { JP_VI: 'jp_vi', VI_JP: 'vi_jp', LISTENING: 'listening' }[mode] || 'jp_vi';
      navigate(`/luyen-tap/quiz?quick=1&mode=${modeParam}`);
    } catch (e) {
      setStartError(getErrorMessage(e, 'Không bắt đầu được buổi luyện.'));
    } finally {
      setStarting(false);
    }
  };

  const handleAddToSet = async () => {
    setAddError('');
    if (totalWords < 1) return;
    setAdding(true);
    try {
      const wordIds = await collectWordIds();
      if (wordIds.length === 0) {
        throw new Error('Không có từ nào trong lựa chọn hiện tại.');
      }
      setAddWords(wordIds.map((id) => ({ id })));
      setAddOpen(true);
    } catch (e) {
      setAddError(getErrorMessage(e, 'Không lấy được danh sách từ.'));
    } finally {
      setAdding(false);
    }
  };

  const handleKanjiFlashcard = () => {
    if (totalKanji === 0) return;
    const arr = Object.values(pickedKanji).map((k) => ({
      character: k.character,
      onYomi: k.onYomi,
      kunYomi: k.kunYomi,
      meaningVi: k.meaningVi,
      strokes: k.strokes,
      jlpt: k.jlpt,
    }));
    sessionStorage.setItem(KANJI_PICK_KEY, JSON.stringify(arr));
    navigate('/luyen-tap/kanji?pick=1');
  };

  if (loading) return <div className="page"><p className="muted">Đang tải…</p></div>;

  const renderLessonBody = (lesson) => {
    const lid = lesson.id;
    const data = lessonData[lid];
    if (!data || data.loading) return <p className="muted">Đang tải dữ liệu bài…</p>;
    if (data.error && !data.full) return <div className="alert alert-error">{data.error}</div>;
    const words = data.words || [];
    const kanjiList = data.kanji || [];
    const clusters = data.clusters || [];
    const tickedCount = clusters.filter((c) => tickedClusters[clusterKey(lid, c.cluster)]).length;
    const effWords = words.filter((w) => isWordEffective(lid, w)).length;
    const pickedChars = kanjiList.filter((k) => pickedKanji[k.character]).length;
    return (
      <div className="qp-groups">
        <div className="qp-group">
          <div className="qp-group-head">
            <strong>📚 Cụm chủ đề</strong>
            <span className="muted">{tickedCount}/{clusters.length} cụm</span>
            <span className="qp-mini-actions">
              <button type="button" className="btn btn-ghost btn-sm" onClick={() => setAllClusters(lid, true)}>Chọn hết</button>
              <button type="button" className="btn btn-ghost btn-sm" onClick={() => setAllClusters(lid, false)}>Bỏ hết</button>
            </span>
          </div>
          {clusters.length === 0 ? (
            <p className="muted">Bài này chưa có cụm.</p>
          ) : (
            <div className="cluster-grid">
              {clusters.map((c) => {
                const key = clusterKey(lid, c.cluster);
                return (
                  <label key={c.cluster} className={`cluster-item${tickedClusters[key] ? ' selected' : ''}`}>
                    <input type="checkbox" checked={!!tickedClusters[key]}
                      onChange={() => toggleCluster(lid, c.cluster)} />
                    <span className="cluster-name">{c.label || clusterLabel(c.cluster)}</span>
                    <span className="cluster-count">{c.count} từ</span>
                  </label>
                );
              })}
            </div>
          )}
        </div>

        <div className="qp-group">
          <div className="qp-group-head">
            <strong>🔤 Từ vựng</strong>
            <span className="muted">{effWords}/{words.length} từ</span>
            <span className="qp-mini-actions">
              <button type="button" className="btn btn-ghost btn-sm" onClick={() => setAllWords(lid, true)}>Chọn tất cả</button>
              <button type="button" className="btn btn-ghost btn-sm" onClick={() => setAllWords(lid, false)}>Bỏ tất cả</button>
            </span>
          </div>
          {words.length === 0 ? (
            <p className="muted">Bài này chưa có từ.</p>
          ) : (
            <div className="qp-words">
              {words.map((w) => {
                const on = isWordEffective(lid, w);
                return (
                  <label key={w.id} className={`qp-word-item${on ? ' selected' : ''}`} title={w.meaningVi}>
                    <input type="checkbox" checked={on} onChange={() => toggleWord(lid, w)} />
                    <span className="qp-word-ja">{w.kanji || w.kana}</span>
                    {w.kanji && <span className="qp-word-kana">{w.kana}</span>}
                    <span className="qp-word-vi">{w.meaningVi}</span>
                  </label>
                );
              })}
            </div>
          )}
        </div>

        <div className="qp-group">
          <div className="qp-group-head">
            <strong>🈁 Kanji</strong>
            <span className="muted">{pickedChars}/{kanjiList.length} chữ</span>
            <span className="qp-mini-actions">
              <button type="button" className="btn btn-ghost btn-sm" onClick={() => setAllKanji(lid, true)}>Chọn hết</button>
              <button type="button" className="btn btn-ghost btn-sm" onClick={() => setAllKanji(lid, false)}>Bỏ hết</button>
            </span>
          </div>
          {kanjiList.length === 0 ? (
            <p className="muted">Bài này chưa có kanji.</p>
          ) : (
            <div className="qp-kanji-grid">
              {kanjiList.map((k) => (
                <label key={k.character} className={`qp-kanji-item${pickedKanji[k.character] ? ' selected' : ''}`} title={k.meaningVi}>
                  <input type="checkbox" checked={!!pickedKanji[k.character]} onChange={() => toggleKanji(k)} />
                  <span className="qp-kanji-char">{k.character}</span>
                  <span className="qp-kanji-vi">{k.meaningVi}</span>
                </label>
              ))}
            </div>
          )}
        </div>
      </div>
    );
  };

  return (
    <div className="page">
      <h1>⚡ Luyện tập nhanh</h1>
      <p className="muted">
        Mở từng bài, tick cụm chủ đề / từ vựng / kanji muốn luyện.
        Dữ liệu mỗi bài chỉ tải khi bạn mở lần đầu.
      </p>
      {error && <div className="alert alert-error">{error}</div>}

      <div className="qp-sec-head">
        <span className="muted">50 bài · Minna no Nihongo</span>
        {hasSelection && (
          <button type="button" className="btn btn-ghost btn-sm" onClick={clearAll}>
            Bỏ chọn hết
          </button>
        )}
      </div>

      {groups.map((g) => g.lessons.length > 0 && (
        <div key={g.level} className="qp-level-group">
          <div className="qp-level-title">{g.label}</div>
          {g.lessons.map((lesson) => {
            const lid = lesson.id;
            const open = !!expanded[lid];
            const data = lessonData[lid];
            const selSummary = [];
            const cc = Object.keys(tickedClusters).filter((k) => k.startsWith(`${lid}:`)).length;
            if (cc > 0) selSummary.push(`${cc} cụm`);
            if (data?.words?.length) {
              const ew = data.words.filter((w) => isWordEffective(lid, w)).length;
              if (ew > 0) selSummary.push(`${ew} từ`);
            }
            if (data?.kanji?.length) {
              const ek = data.kanji.filter((k) => pickedKanji[k.character]).length;
              if (ek > 0) selSummary.push(`${ek} chữ`);
            }
            return (
              <div key={lid} className={`qp-acc${open ? ' open' : ''}`}>
                <button type="button" className="qp-acc-head" onClick={() => toggleExpand(lid)} aria-expanded={open}>
                  <span className="qp-acc-chevron">{open ? '▾' : '▸'}</span>
                  <span className="qp-acc-title">Bài {lesson.lessonNo} — {lesson.titleVi}</span>
                  {selSummary.length > 0 && (
                    <span className="qp-acc-sel">✓ {selSummary.join(' · ')}</span>
                  )}
                  {data?.loading && <span className="muted">đang tải…</span>}
                </button>
                {open && (
                  <div className="qp-acc-body">
                    {renderLessonBody(lesson)}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      ))}

      <div className="qp-sticky">
        <div className="qp-total">
          <span className="qp-total-num">{totalWords}</span>
          <span className="muted">từ</span>
          <span className="qp-plus">+</span>
          <span className="qp-total-num">{totalKanji}</span>
          <span className="muted">chữ</span>
          {totalWords > MAX_WORDS && (
            <span className="alert-inline">Tối đa {MAX_WORDS} từ — hãy bỏ bớt.</span>
          )}
          {totalWords > 0 && totalWords < MIN_WORDS && (
            <span className="alert-inline">Cần ít nhất {MIN_WORDS} từ để luyện nhanh.</span>
          )}
        </div>
        <div className="qp-actions">
          <span className="muted">Dạng quiz:</span>
          <select className="select-inline" value={mode} onChange={(e) => setMode(e.target.value)} aria-label="Dạng quiz">
            {QUIZ_MODES.map((m) => <option key={m.value} value={m.value}>{m.label}</option>)}
          </select>
          <button type="button" className="btn btn-outline" disabled={!canAddToSet} onClick={handleAddToSet}>
            {adding ? 'Đang lấy từ…' : '＋ Thêm vào bộ thẻ'}
          </button>
          <button type="button" className="btn btn-outline" disabled={totalKanji === 0} onClick={handleKanjiFlashcard}>
            🃏 Flashcard {totalKanji} chữ
          </button>
          <button type="button" className="btn btn-primary btn-lg" disabled={!canStart} onClick={handleStart}>
            {starting ? 'Đang chuẩn bị…' : '⚡ Luyện tập nhanh'}
          </button>
        </div>
      </div>
      {startError && <div className="alert alert-error">{startError}</div>}
      {addError && <div className="alert alert-error">{addError}</div>}
      <AddToSetModal open={addOpen} onClose={() => setAddOpen(false)} words={addWords} />
    </div>
  );
}
