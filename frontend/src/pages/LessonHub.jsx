import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { fetchLesson, fetchLessonClusters, fetchLessonWords } from '../api/lessons.js';
import { fetchGrammar } from '../api/grammar.js';
import { fetchKanji, generateKanjiQuiz } from '../api/kanji.js';
import { fetchProgressSummary } from '../api/study.js';
import { getErrorMessage } from '../api/client.js';
import { useAuth } from '../context/AuthContext.jsx';
import GrammarPointCard from '../components/GrammarPointCard.jsx';
import GrammarRelatedModal from '../components/GrammarRelatedModal.jsx';
import AudioButton from '../components/AudioButton.jsx';
import LoginPrompt from '../components/LoginPrompt.jsx';
import AddToSetModal from '../components/AddToSetModal.jsx';
import Flashcard from './Flashcard.jsx';
import Quiz from './Quiz.jsx';
import Typing from './Typing.jsx';
import KanjiFlashcard from './KanjiFlashcard.jsx';
import KanjiQuiz from './KanjiQuiz.jsx';
import { PencilIcon, SpeakerIcon } from '../components/icons.jsx';
import { SECTION_TABS, clusterLabel } from '../utils.js';
import './pages.css';

// v2.0 (A3): Lesson Hub (/hoc/bai/:id) — mọi nội dung của 1 bài (Từ vựng,
// Cụm chủ đề, Ngữ pháp, Kanji) trong 1 trang; "Luyện tập" là mode picker
// bên trong bài, render component practice hiện có (truyền lessonId),
// KHÔNG viết lại logic chấm điểm / SM-2.
// Dữ liệu: đúng API/DTO hiện có của LessonDetail, Grammar, Kanji.
const QUICK_KEY = 'mjp_quick_quiz';
const KANJI_QUIZ_KEY = 'mjp_kanji_quiz';
const FLASHCARD_DIRS = [
  { value: 'JP_VI', label: 'Nhật → Việt' },
  { value: 'VI_JP', label: 'Việt → Nhật' },
];
const QUIZ_MODES = [
  { value: 'JP_VI', label: 'Nhật → Việt' },
  { value: 'VI_JP', label: 'Việt → Nhật' },
];
const KANJI_QUIZ_MODES = [
  { value: 'KANJI_MEANING', label: 'Chữ → Nghĩa' },
  { value: 'MEANING_KANJI', label: 'Nghĩa → Chữ' },
  { value: 'KANJI_TYPING', label: 'Gõ cách đọc' },
];
const PRACTICE_TITLES = {
  flashcard: 'Flashcard 3D',
  quiz: 'Quiz',
  listening: 'Luyện nghe',
  typing: 'Gõ từ',
  'quick-flashcard': 'Flashcard — từ đã chọn',
  'quick-quiz': 'Quiz — từ đã chọn',
  'quick-typing': 'Gõ từ — từ đã chọn',
  'kanji-flash': 'Flashcard kanji',
  'kanji-quiz': 'Quiz kanji',
};

export default function LessonHub() {
  const { id } = useParams();
  const { isLoggedIn } = useAuth();
  const practiceRef = useRef(null);

  const [lesson, setLesson] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [lessonProgress, setLessonProgress] = useState(null); // { total, mastered }

  const [tab, setTab] = useState('vocab'); // 'vocab' | 'cluster' | 'grammar' | 'kanji'
  const [section, setSection] = useState('VOCAB');
  const [words, setWords] = useState([]);
  const [wordsLoading, setWordsLoading] = useState(false);
  const [selected, setSelected] = useState({});

  const [clusters, setClusters] = useState([]); // [{ cluster, label, count }]
  const [selectedClusters, setSelectedClusters] = useState({});

  const [grammarItems, setGrammarItems] = useState(null);
  const [grammarLoading, setGrammarLoading] = useState(false);
  const [grammarError, setGrammarError] = useState('');
  const [grammarRetry, setGrammarRetry] = useState(0);
  const [relatedId, setRelatedId] = useState(null);

  const [kanjiItems, setKanjiItems] = useState(null);
  const [kanjiLoading, setKanjiLoading] = useState(false);
  const [kanjiError, setKanjiError] = useState('');
  const [kanjiRetry, setKanjiRetry] = useState(0);

  const [practiceMode, setPracticeMode] = useState(null);
  const [quizMode, setQuizMode] = useState('JP_VI');
  const [flashcardDir, setFlashcardDir] = useState('JP_VI');
  const [kanjiQuizMode, setKanjiQuizMode] = useState('KANJI_MEANING');
  const [kanjiQuizKey, setKanjiQuizKey] = useState(0);
  const [kanjiQuizBusy, setKanjiQuizBusy] = useState(false);

  const [loginPrompt, setLoginPrompt] = useState('');
  const [addSetOpen, setAddSetOpen] = useState(false);

  // --- Tải bài học (reset state theo bài) ---
  useEffect(() => {
    setLoading(true);
    setError('');
    setLesson(null);
    setLessonProgress(null);
    setTab('vocab');
    setSection('VOCAB');
    setSelected({});
    setSelectedClusters({});
    setGrammarItems(null);
    setKanjiItems(null);
    setClusters([]);
    setWords([]);
    setPracticeMode(null);
    setRelatedId(null);
    fetchLesson(id)
      .then((l) => setLesson(l))
      .catch((e) => setError(getErrorMessage(e, 'Không tải được bài học.')))
      .finally(() => setLoading(false));
  }, [id]);

  // --- Tiến trình bài (user đã đăng nhập) — từ API progress/summary hiện có ---
  useEffect(() => {
    if (!isLoggedIn) { setLessonProgress(null); return; }
    fetchProgressSummary()
      .then((s) => {
        const found = (s.lessons || []).find((x) => (x.lessonId || x.id) === id);
        setLessonProgress(found ? { total: found.total || 0, mastered: found.mastered || 0 } : null);
      })
      .catch(() => setLessonProgress(null));
  }, [id, isLoggedIn]);

  // --- Tải dữ liệu theo tab ---
  useEffect(() => {
    if (!lesson) return;
    if (tab === 'vocab') {
      setWordsLoading(true);
      fetchLessonWords(id, section)
        .then((w) => { setWords(Array.isArray(w) ? w : []); setSelected({}); })
        .catch((e) => setError(getErrorMessage(e, 'Không tải được từ vựng.')))
        .finally(() => setWordsLoading(false));
    } else if (tab === 'cluster') {
      setWordsLoading(true);
      Promise.all([fetchLessonWords(id), fetchLessonClusters(id)])
        .then(([w, c]) => {
          setWords(Array.isArray(w) ? w : []);
          setClusters(Array.isArray(c) ? c : []);
          setSelected({});
        })
        .catch((e) => setError(getErrorMessage(e, 'Không tải được cụm chủ đề.')))
        .finally(() => setWordsLoading(false));
    } else if (tab === 'grammar') {
      if (grammarItems) return;
      setGrammarLoading(true);
      setGrammarError('');
      fetchGrammar({ lessonNo: lesson.lessonNo })
        .then((items) => setGrammarItems(items || []))
        .catch((e) => setGrammarError(getErrorMessage(e, 'Không tải được ngữ pháp bài này.')))
        .finally(() => setGrammarLoading(false));
    } else if (tab === 'kanji') {
      if (kanjiItems) return;
      setKanjiLoading(true);
      setKanjiError('');
      fetchKanji({ lessonNo: lesson.lessonNo })
        .then((items) => setKanjiItems(items || []))
        .catch((e) => setKanjiError(getErrorMessage(e, 'Không tải được kanji bài này.')))
        .finally(() => setKanjiLoading(false));
    }
  }, [id, tab, section, lesson, grammarItems, kanjiItems, grammarRetry, kanjiRetry]);

  const grammarTitles = useMemo(() => {
    const map = {};
    for (const p of grammarItems || []) if (p?.id && p?.title) map[p.id] = p.title;
    return map;
  }, [grammarItems]);

  const toggleSelect = (word) => {
    setSelected((prev) => {
      const next = { ...prev };
      if (next[word.id]) delete next[word.id];
      else next[word.id] = word;
      return next;
    });
  };

  const toggleCluster = (code) => {
    setSelectedClusters((prev) => {
      const next = { ...prev };
      if (next[code]) delete next[code];
      else next[code] = true;
      return next;
    });
  };

  const wordsByCluster = useMemo(() => {
    const map = {};
    for (const w of words) {
      const code = w.cluster || 'khac';
      if (!map[code]) map[code] = [];
      map[code].push(w);
    }
    return map;
  }, [words]);

  const selectedClusterCodes = useMemo(
    () => Object.keys(selectedClusters),
    [selectedClusters],
  );

  // Tập từ đã chọn = tick từng từ + cụm đã tick (gộp theo id).
  const selectionWords = useMemo(() => {
    const byId = {};
    for (const w of Object.values(selected)) byId[w.id] = w;
    for (const code of selectedClusterCodes) {
      for (const w of (wordsByCluster[code] || [])) byId[w.id] = w;
    }
    return Object.values(byId);
  }, [selected, selectedClusterCodes, wordsByCluster]);

  const requireLogin = (message, fn) => {
    if (!isLoggedIn) {
      setLoginPrompt(message);
      return;
    }
    fn();
  };

  const scrollToPractice = () => {
    requestAnimationFrame(() => {
      practiceRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
  };

  const exitPractice = () => setPracticeMode(null);

  const startPractice = (mode) => {
    requireLogin('Đăng nhập để luyện tập và lưu tiến trình.', () => {
      setPracticeMode(mode);
      scrollToPractice();
    });
  };

  // Luyện từ đã chọn — pattern ?quick=1 + sessionStorage (như LessonDetail),
  // nhưng render embedded trong Hub thay vì chuyển trang.
  const startSelectionPractice = (target) => {
    const ws = selectionWords;
    if (ws.length === 0) return;
    const payload = {
      wordIds: ws.map((w) => w.id),
      words: ws,
      returnTo: `/hoc/bai/${encodeURIComponent(id)}`,
    };
    if (target === 'quiz') payload.mode = quizMode;
    else if (target === 'typing') payload.mode = 'TYPING';
    else payload.mode = 'FLASHCARD';
    sessionStorage.setItem(QUICK_KEY, JSON.stringify(payload));
    requireLogin('Đăng nhập để luyện tập và lưu tiến trình.', () => {
      setPracticeMode(`quick-${target}`);
      scrollToPractice();
    });
  };

  // Quiz kanji: sinh câu hỏi qua API hiện có rồi render embedded.
  const startKanjiQuiz = async () => {
    const chars = (kanjiItems || []).map((k) => k.character).filter(Boolean);
    if (chars.length === 0) return;
    requireLogin('Đăng nhập để luyện quiz kanji.', async () => {
      setKanjiQuizBusy(true);
      try {
        const data = await generateKanjiQuiz({ characters: chars, mode: kanjiQuizMode });
        const questions = data?.questions || [];
        if (questions.length === 0) throw new Error('Không tạo được câu hỏi cho bài này.');
        sessionStorage.setItem(KANJI_QUIZ_KEY, JSON.stringify({
          questions, mode: kanjiQuizMode, count: questions.length, totalChars: chars.length,
        }));
        setKanjiQuizKey((k) => k + 1);
        setPracticeMode('kanji-quiz');
        scrollToPractice();
      } catch (e) {
        setKanjiError(getErrorMessage(e, 'Không tạo được quiz kanji.'));
      } finally {
        setKanjiQuizBusy(false);
      }
    });
  };

  const progressPct = lessonProgress && lessonProgress.total > 0
    ? Math.round((lessonProgress.mastered / lessonProgress.total) * 100)
    : null;

  if (loading) return <div className="page"><p className="muted">Đang tải bài học…</p></div>;
  if (error && !lesson) return <div className="page"><div className="alert alert-error">{error}</div></div>;
  if (!lesson) return null;

  const tabs = [
    { value: 'vocab', label: 'Từ vựng', count: tab === 'vocab' ? words.length : null },
    { value: 'cluster', label: 'Cụm chủ đề', count: clusters.length || null },
    { value: 'grammar', label: 'Ngữ pháp', count: grammarItems ? grammarItems.length : null },
    { value: 'kanji', label: 'Kanji', count: kanjiItems ? kanjiItems.length : null },
  ];

  return (
    <div className="page">
      {error && <div className="alert alert-error">{error}</div>}

      <div className="hub-crumb">
        <Link to="/hoc">Bài học</Link> / {lesson.level} / <b>Bài {lesson.lessonNo}: {lesson.titleVi}</b>
      </div>

      <div className="hub-head">
        <div className="hub-num"><small>BÀI</small><b>{lesson.lessonNo}</b></div>
        <div>
          <h1>{lesson.titleJa}</h1>
          <div className="hub-sub">
            {lesson.titleVi} · Cấp độ {lesson.level}
            {progressPct != null && ` · ${progressPct}% hoàn thành`}
          </div>
          {progressPct != null && (
            <div className="hub-progress"><i style={{ width: `${progressPct}%` }} /></div>
          )}
        </div>
      </div>

      <div className="hub-tabs" role="tablist">
        {tabs.map((t) => (
          <button
            key={t.value}
            type="button"
            role="tab"
            aria-selected={tab === t.value}
            className={`hub-tab${tab === t.value ? ' active' : ''}`}
            onClick={() => setTab(t.value)}
          >
            {t.label}
            {t.count != null && <span className="n">{t.count}</span>}
          </button>
        ))}
      </div>

      <div className="hub-panel">
        {tab === 'vocab' && (
          <>
            <div className="tabs tabs-sub">
              {SECTION_TABS.map((t) => (
                <button key={t.value} type="button" className={`tab${section === t.value ? ' active' : ''}`}
                  onClick={() => setSection(t.value)}>
                  {t.label}
                </button>
              ))}
            </div>

            <div className="hub-toolbar">
              <span className="muted">{words.length} từ · đã chọn {selectionWords.length}</span>
              {selectionWords.length > 0 && (
                <>
                  <button type="button" className="btn btn-primary btn-sm"
                    onClick={() => requireLogin('Đăng nhập để thêm từ vào bộ thẻ của bạn.', () => setAddSetOpen(true))}>
                    ＋ Thêm {selectionWords.length} từ vào bộ thẻ
                  </button>
                  <button type="button" className="btn btn-outline btn-sm"
                    onClick={() => startSelectionPractice('flashcard')}>
                    Luyện {selectionWords.length} từ đã chọn
                  </button>
                </>
              )}
            </div>

            {wordsLoading ? (
              <p className="muted">Đang tải từ vựng…</p>
            ) : words.length === 0 ? (
              <p className="muted">Phần này chưa có từ vựng.</p>
            ) : (
              <table className="hub-words">
                <thead>
                  <tr>
                    <th style={{ width: 44 }}><span className="sr-only">Chọn</span></th>
                    <th>Từ</th>
                    <th>Âm đọc</th>
                    <th>Nghĩa</th>
                    <th style={{ width: 56 }}><span className="sr-only">Nghe</span></th>
                  </tr>
                </thead>
                <tbody>
                  {words.map((w) => (
                    <tr key={w.id}>
                      <td>
                        <input
                          type="checkbox"
                          checked={!!selected[w.id]}
                          onChange={() => toggleSelect(w)}
                          aria-label={`Chọn từ ${w.kana || w.kanji}`}
                        />
                      </td>
                      <td>
                        <span className="hub-jp">{w.kanji || w.kana}</span>
                        {w.kanji && <span className="hub-kana">{w.kana}</span>}
                      </td>
                      <td><span className="hub-romaji">{w.romaji}</span></td>
                      <td>{w.meaningVi}</td>
                      <td><AudioButton url={w.audioUrl} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </>
        )}

        {tab === 'cluster' && (
          <>
            <p className="muted" style={{ marginTop: 0 }}>
              Từ vựng được nhóm thành cụm chủ đề — bấm vào cụm để xem từ bên trong, chọn từng từ rồi luyện hoặc thêm vào bộ thẻ.
            </p>
            {wordsLoading ? (
              <p className="muted">Đang tải cụm chủ đề…</p>
            ) : clusters.length === 0 ? (
              <p className="muted">Bài này chưa có cụm chủ đề nào.</p>
            ) : (
              <>
                <div className="hub-cluster-grid">
                  {clusters.map((c) => (
                    <button
                      key={c.cluster}
                      type="button"
                      className={`hub-cluster${selectedClusters[c.cluster] ? ' selected' : ''}`}
                      onClick={() => toggleCluster(c.cluster)}
                      aria-pressed={!!selectedClusters[c.cluster]}
                    >
                      <span className="cnt">{c.count} từ</span>
                      <h4>{c.label || clusterLabel(c.cluster)}</h4>
                      <p>{((wordsByCluster[c.cluster] || []).slice(0, 4).map((w) => w.kana || w.kanji).join(' · ') || '')}</p>
                    </button>
                  ))}
                </div>
                {selectedClusterCodes.length > 0 && (
                  <div className="hub-toolbar" style={{ marginTop: 18 }}>
                    <span className="muted">Đã chọn {selectionWords.length} từ</span>
                    <button type="button" className="btn btn-primary btn-sm"
                      onClick={() => startSelectionPractice('flashcard')}>
                      Luyện {selectionWords.length} từ đã chọn
                    </button>
                    <button type="button" className="btn btn-outline btn-sm"
                      onClick={() => requireLogin('Đăng nhập để thêm từ vào bộ thẻ của bạn.', () => setAddSetOpen(true))}>
                      ＋ Thêm vào bộ thẻ
                    </button>
                  </div>
                )}
              </>
            )}
          </>
        )}

        {tab === 'grammar' && (
          <>
            {grammarLoading ? (
              <p className="muted">Đang tải ngữ pháp…</p>
            ) : grammarError ? (
              <>
                <div className="alert alert-error">{grammarError}</div>
                <button type="button" className="btn btn-outline btn-sm" onClick={() => setGrammarRetry((r) => r + 1)}>
                  Thử lại
                </button>
              </>
            ) : !grammarItems || grammarItems.length === 0 ? (
              <p className="muted">Bài này chưa có dữ liệu ngữ pháp.</p>
            ) : (
              <div className="grammar-list">
                {grammarItems.map((p) => (
                  <div key={p.id}>
                    <GrammarPointCard
                      point={p}
                      relatedTitles={grammarTitles}
                      onRelated={(gid) => setRelatedId(gid)}
                    />
                    <div className="hub-toolbar" style={{ marginTop: 10 }}>
                      <button type="button" className="btn btn-primary btn-sm"
                        onClick={() => startPractice('quiz')}>
                        Luyện ngay
                      </button>
                      <span className="muted" style={{ fontSize: '0.82rem' }}>Làm quiz từ vựng bài {lesson.lessonNo} để ghi nhớ mẫu câu</span>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </>
        )}

        {tab === 'kanji' && (
          <>
            <div className="hub-toolbar">
              <span className="muted">{kanjiItems ? `${kanjiItems.length} chữ trong bài này` : 'Kanji xuất hiện trong bài'}</span>
              {kanjiItems && kanjiItems.length > 0 && (
                <>
                  <button type="button" className="btn btn-primary btn-sm"
                    onClick={() => startPractice('kanji-flash')}>
                    Flashcard kanji
                  </button>
                  <select className="select-inline" value={kanjiQuizMode}
                    onChange={(e) => setKanjiQuizMode(e.target.value)} aria-label="Dạng quiz kanji">
                    {KANJI_QUIZ_MODES.map((m) => <option key={m.value} value={m.value}>{m.label}</option>)}
                  </select>
                  <button type="button" className="btn btn-outline btn-sm"
                    onClick={startKanjiQuiz} disabled={kanjiQuizBusy}>
                    {kanjiQuizBusy ? 'Đang tạo…' : 'Quiz kanji'}
                  </button>
                </>
              )}
            </div>
            {kanjiLoading ? (
              <p className="muted">Đang tải kanji…</p>
            ) : kanjiError ? (
              <>
                <div className="alert alert-error">{kanjiError}</div>
                <button type="button" className="btn btn-outline btn-sm" onClick={() => setKanjiRetry((r) => r + 1)}>
                  Thử lại
                </button>
              </>
            ) : !kanjiItems || kanjiItems.length === 0 ? (
              <p className="muted">Bài này chưa có dữ liệu kanji.</p>
            ) : (
              <div className="hub-kanji-grid">
                {kanjiItems.map((k) => (
                  <Link key={k.character} to={`/kanji/${encodeURIComponent(k.character)}`} className="hub-kanji"
                    title={`${k.meaningVi || k.character} — xem âm đọc, nghĩa và số nét`}>
                    <div className="ch">{k.character}</div>
                    <div className="rd">
                      {[...(k.onYomi || []), ...(k.kunYomi || [])].slice(0, 2).join(' · ')}
                      {k.meaningVi ? ` — ${k.meaningVi}` : ''}
                    </div>
                  </Link>
                ))}
              </div>
            )}
          </>
        )}
      </div>

      {/* --- Khối Luyện tập: mode picker trong bài (A3) --- */}
      <div className="hub-practice" ref={practiceRef}>
        <div className="hub-practice-head">
          <h2>Luyện tập bài {lesson.lessonNo}</h2>
          {practiceMode && (
            <button type="button" className="btn btn-outline btn-sm" onClick={exitPractice}>
              ← Thoát luyện tập
            </button>
          )}
        </div>
        <p className="muted" style={{ margin: '0 0 4px' }}>Chọn một chế độ — tiến trình được lưu ngay sau mỗi câu.</p>
        <div className="hub-modes">
          {[
            { mode: 'flashcard', badge: <span className="jp-badge shu">札</span>, title: 'Flashcard 3D',
              desc: 'Thẻ lật 3D. Tự đánh giá Nhớ / Chưa nhớ — thoát giữa chừng cũng không mất tiến trình.',
              control: (
                <select className="select-inline" value={flashcardDir}
                  onClick={(e) => e.stopPropagation()}
                  onChange={(e) => setFlashcardDir(e.target.value)} aria-label="Hướng flashcard">
                  {FLASHCARD_DIRS.map((m) => <option key={m.value} value={m.value}>{m.label}</option>)}
                </select>
              ) },
            { mode: 'quiz', badge: <span className="jp-badge">問</span>, title: 'Quiz',
              desc: 'Chọn đáp án đúng trong 4 lựa chọn. Chấm ngay từng câu, cuối phiên có danh sách câu sai.',
              control: (
                <select className="select-inline" value={quizMode}
                  onClick={(e) => e.stopPropagation()}
                  onChange={(e) => setQuizMode(e.target.value)} aria-label="Dạng trắc nghiệm">
                  {QUIZ_MODES.map((m) => <option key={m.value} value={m.value}>{m.label}</option>)}
                </select>
              ) },
            { mode: 'listening', badge: <SpeakerIcon size={22} />, title: 'Luyện nghe',
              desc: 'Nghe phát âm và chọn từ đúng — rèn phản xạ nghe hiểu.' },
            { mode: 'typing', badge: <PencilIcon size={22} />, title: 'Gõ từ',
              desc: 'Gõ phiên âm bằng romaji (tự chuyển hiragana), chấm ngay từng từ.' },
          ].map((m) => (
            <div
              key={m.mode}
              role="button"
              tabIndex={0}
              className={`hub-mode${practiceMode === m.mode ? ' active' : ''}`}
              onClick={() => startPractice(m.mode)}
              onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); startPractice(m.mode); } }}
            >
              <h4>{m.badge} {m.title}</h4>
              <p>{m.desc}</p>
              {m.control}
            </div>
          ))}
        </div>

        {practiceMode && (
          <div className="hub-practice-view">
            {practiceMode === 'flashcard' && (
              <Flashcard embedded scopeType="LESSON" scopeId={id}
                direction={flashcardDir.toLowerCase()} onExit={exitPractice} />
            )}
            {practiceMode === 'quiz' && (
              <Quiz embedded scopeType="LESSON" scopeId={id} mode={quizMode} onExit={exitPractice} />
            )}
            {practiceMode === 'listening' && (
              <Quiz embedded scopeType="LESSON" scopeId={id} mode="LISTENING" onExit={exitPractice} />
            )}
            {practiceMode === 'typing' && (
              <Typing embedded scopeType="LESSON" scopeId={id} onExit={exitPractice} />
            )}
            {practiceMode === 'quick-flashcard' && (
              <Flashcard embedded scopeType="QUICK" scopeId="quick"
                direction={flashcardDir.toLowerCase()} onExit={exitPractice} />
            )}
            {practiceMode === 'quick-quiz' && (
              <Quiz embedded quick scopeType="QUICK" scopeId="quick" onExit={exitPractice} />
            )}
            {practiceMode === 'quick-typing' && (
              <Typing embedded quick scopeType="QUICK" scopeId="quick" onExit={exitPractice} />
            )}
            {practiceMode === 'kanji-flash' && (
              <KanjiFlashcard embedded lessonNo={lesson.lessonNo} onExit={exitPractice} />
            )}
            {practiceMode === 'kanji-quiz' && (
              <KanjiQuiz key={kanjiQuizKey} onExit={exitPractice} />
            )}
          </div>
        )}
      </div>

      <GrammarRelatedModal
        grammarId={relatedId}
        relatedTitles={grammarTitles}
        onClose={() => setRelatedId(null)}
        onRelated={(gid) => setRelatedId(gid)}
      />
      <LoginPrompt open={!!loginPrompt} onClose={() => setLoginPrompt('')} message={loginPrompt} />
      <AddToSetModal open={addSetOpen} onClose={() => setAddSetOpen(false)} words={selectionWords} />
    </div>
  );
}
