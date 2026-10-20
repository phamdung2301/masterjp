import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { fetchLesson, fetchLessonClusters, fetchLessonWords } from '../api/lessons.js';
import { fetchGrammar } from '../api/grammar.js';
import { getErrorMessage } from '../api/client.js';
import { useAuth } from '../context/AuthContext.jsx';
import WordCard from '../components/WordCard.jsx';
import GrammarPointCard from '../components/GrammarPointCard.jsx';
import GrammarRelatedModal from '../components/GrammarRelatedModal.jsx';
import AudioButton from '../components/AudioButton.jsx';
import LoginPrompt from '../components/LoginPrompt.jsx';
import AddToSetModal from '../components/AddToSetModal.jsx';
import { QUIZ_MODES, SECTION_LABELS, SECTION_TABS, STATUS_COLORS, STATUS_LABELS, clusterLabel } from '../utils.js';
import './pages.css';

// US-003 (F-011, F-013): chi tiết bài — 3 tab phần, mỗi từ có nút phát audio,
// tick chọn nhiều từ → thêm vào bộ thẻ, nút vào flashcard/quiz theo bài hoặc từng tab.
// US-016: guest xem read-only; bấm "Luyện tập"/"Thêm vào bộ thẻ" → mời đăng nhập.
// F-A: chế độ xem "Theo cụm" — nhóm từ theo cụm chủ đề (GET /api/lessons/{id}/clusters),
// checkbox từng cụm + nút "Luyện cụm đã chọn" → trang luyện tập nhanh.
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
  // Hướng flashcard: JP_VI = mặt trước tiếng Nhật, VI_JP = mặt trước nghĩa Việt.
  const [flashcardDir, setFlashcardDir] = useState('JP_VI');
  const FLASHCARD_DIRS = [
    { value: 'JP_VI', label: 'Nhật → Việt' },
    { value: 'VI_JP', label: 'Việt → Nhật' },
  ];

  // F-A: chế độ xem theo cụm
  const [viewMode, setViewMode] = useState('section'); // 'section' | 'cluster' | 'grammar'
  const [clusters, setClusters] = useState([]); // [{ cluster, label, count }]
  const [selectedClusters, setSelectedClusters] = useState({});

  // Ngữ pháp: view đọc, fetch 1 lần khi chọn tab (cache theo bài).
  const [grammarItems, setGrammarItems] = useState(null);
  const [grammarLoading, setGrammarLoading] = useState(false);
  const [grammarError, setGrammarError] = useState('');
  const [relatedId, setRelatedId] = useState(null);

  useEffect(() => {
    setLoading(true);
    setError('');
    if (viewMode === 'cluster') {
      // Chế độ cụm: tải toàn bộ từ trong bài (không lọc phần) + danh sách cụm.
      Promise.all([fetchLesson(id), fetchLessonWords(id), fetchLessonClusters(id)])
        .then(([l, w, c]) => {
          setLesson(l);
          setWords(w);
          setClusters(c || []);
          setSelected({});
        })
        .catch((e) => setError(getErrorMessage(e, 'Không tải được bài học.')))
        .finally(() => setLoading(false));
    } else if (viewMode === 'grammar') {
      // Tab ngữ pháp: chỉ cần lesson (để có lessonNo), không fetch từ vựng.
      fetchLesson(id)
        .then((l) => setLesson(l))
        .catch((e) => setError(getErrorMessage(e, 'Không tải được bài học.')))
        .finally(() => setLoading(false));
    } else {
      Promise.all([fetchLesson(id), fetchLessonWords(id, section)])
        .then(([l, w]) => { setLesson(l); setWords(w); setSelected({}); })
        .catch((e) => setError(getErrorMessage(e, 'Không tải được bài học.')))
        .finally(() => setLoading(false));
    }
  }, [id, section, viewMode]);

  // Đổi bài → reset cache ngữ pháp của bài cũ.
  useEffect(() => {
    setGrammarItems(null);
    setGrammarError('');
    setRelatedId(null);
  }, [id]);

  // Ngữ pháp: fetch 1 lần khi lesson đã tải và tab grammar đang chọn (cache theo bài).
  useEffect(() => {
    if (viewMode !== 'grammar') return;
    const lessonNo = lesson?.lessonNo;
    if (lessonNo == null || grammarItems) return;
    setGrammarLoading(true);
    setGrammarError('');
    fetchGrammar({ lessonNo })
      .then((items) => setGrammarItems(items || []))
      .catch((e) => setGrammarError(getErrorMessage(e, 'Không tải được ngữ pháp bài này.')))
      .finally(() => setGrammarLoading(false));
  }, [viewMode, lesson, grammarItems]);

  // Map id → title cho chip compareWith (từ list ngữ pháp của bài này).
  const grammarTitles = useMemo(() => {
    const map = {};
    for (const p of grammarItems || []) if (p?.id && p?.title) map[p.id] = p.title;
    return map;
  }, [grammarItems]);

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
    navigate(`/luyen-tap/flashcard?direction=${flashcardDir.toLowerCase()}&scopeType=${scopeType}&scopeId=${encodeURIComponent(scopeId)}`);

  const goQuiz = (scopeType, scopeId) =>
    navigate(`/luyen-tap/quiz?mode=${quizMode.toLowerCase()}&scopeType=${scopeType}&scopeId=${encodeURIComponent(scopeId)}`);

  const goTyping = (scopeType, scopeId) =>
    navigate(`/luyen-tap/go-phien-am?scopeType=${scopeType}&scopeId=${encodeURIComponent(scopeId)}`);

  const sectionId = `${id}:${section}`;

  // F-A: gom từ theo cụm (cluster null → 'khac').
  const wordsByCluster = useMemo(() => {
    const map = {};
    for (const w of words) {
      const code = w.cluster || 'khac';
      if (!map[code]) map[code] = [];
      map[code].push(w);
    }
    return map;
  }, [words]);

  const toggleCluster = (code) => {
    setSelectedClusters((prev) => {
      const next = { ...prev };
      if (next[code]) delete next[code];
      else next[code] = true;
      return next;
    });
  };

  const selectedClusterCodes = Object.keys(selectedClusters);
  // Chưa chọn cụm nào → hiện toàn bộ (theo thứ tự của API: count giảm dần).
  const visibleClusters = clusters.filter((c) =>
    selectedClusterCodes.length === 0 || selectedClusters[c.cluster]
  );

  // R2: tập từ đã chọn = tick từng từ (2 chế độ xem) + cụm đã tick (chế độ cụm), gộp theo id.
  const selectionWords = useMemo(() => {
    const byId = {};
    for (const w of selectedWords) byId[w.id] = w;
    if (viewMode === 'cluster') {
      for (const code of selectedClusterCodes) {
        for (const w of (wordsByCluster[code] || [])) byId[w.id] = w;
      }
    }
    return Object.values(byId);
  }, [selectedWords, viewMode, selectedClusterCodes, wordsByCluster]);

  // R2: thanh luyện tập thống nhất — lưu payload vào sessionStorage (key chung
  // với trang Luyện tập nhanh: 'mjp_quick_quiz', thêm full objects cho Flashcard)
  // rồi sang trang luyện với ?quick=1.
  const QUICK_KEY = 'mjp_quick_quiz';
  const QUIZ_URL_MODE = { JP_VI: 'jp_vi', VI_JP: 'vi_jp', LISTENING: 'listening' };
  const startSelectionPractice = (target) => {
    const words = selectionWords;
    const wordIds = words.map((w) => w.id);
    const returnTo = `/bai-hoc/${encodeURIComponent(id)}`;
    if (target === 'quiz') {
      sessionStorage.setItem(QUICK_KEY, JSON.stringify({ wordIds, words, mode: quizMode, returnTo }));
      navigate(`/luyen-tap/quiz?quick=1&mode=${QUIZ_URL_MODE[quizMode] || 'jp_vi'}`);
    } else if (target === 'typing') {
      sessionStorage.setItem(QUICK_KEY, JSON.stringify({ wordIds, words, mode: 'TYPING', returnTo }));
      navigate('/luyen-tap/go-phien-am?quick=1');
    } else {
      sessionStorage.setItem(QUICK_KEY, JSON.stringify({ wordIds, words, mode: 'FLASHCARD', returnTo }));
      navigate(`/luyen-tap/flashcard?quick=1&direction=${flashcardDir.toLowerCase()}`);
    }
  };

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

          {viewMode !== 'grammar' && (
          <div className="practice-bar">
            <div className="practice-group">
              <span className="practice-label">Luyện cả bài:</span>
              <span className="practice-pair">
                <button type="button" className="btn btn-primary btn-sm"
                  onClick={() => requireLogin('Đăng nhập để luyện flashcard và lưu tiến trình.', () => goFlashcard('LESSON', id))}>
                  🃏 Flashcard
                </button>
                <select className="select-inline" value={flashcardDir} onChange={(e) => setFlashcardDir(e.target.value)} aria-label="Hướng flashcard">
                  {FLASHCARD_DIRS.map((m) => <option key={m.value} value={m.value}>{m.label}</option>)}
                </select>
              </span>
              <span className="practice-pair">
                <button type="button" className="btn btn-outline btn-sm"
                  onClick={() => requireLogin('Đăng nhập để làm quiz và lưu tiến trình.', () => goQuiz('LESSON', id))}>
                  ✏️ Quiz
                </button>
                <select className="select-inline" value={quizMode} onChange={(e) => setQuizMode(e.target.value)} aria-label="Dạng quiz">
                  {QUIZ_MODES.map((m) => <option key={m.value} value={m.value}>{m.label}</option>)}
                </select>
              </span>
              <button type="button" className="btn btn-outline btn-sm"
                onClick={() => requireLogin('Đăng nhập để luyện gõ phiên âm và lưu tiến trình.', () => goTyping('LESSON', id))}>
                ⌨️ Gõ phiên âm
              </button>
            </div>
            {viewMode === 'section' && (
              <div className="practice-group">
                <span className="practice-label">Luyện phần này ({SECTION_LABELS[section]}):</span>
                <span className="practice-pair">
                  <button type="button" className="btn btn-primary btn-sm"
                    onClick={() => requireLogin('Đăng nhập để luyện flashcard và lưu tiến trình.', () => goFlashcard('SECTION', sectionId))}>
                    🃏 Flashcard
                  </button>
                  <select className="select-inline" value={flashcardDir} onChange={(e) => setFlashcardDir(e.target.value)} aria-label="Hướng flashcard">
                    {FLASHCARD_DIRS.map((m) => <option key={m.value} value={m.value}>{m.label}</option>)}
                  </select>
                </span>
                <span className="practice-pair">
                  <button type="button" className="btn btn-outline btn-sm"
                    onClick={() => requireLogin('Đăng nhập để làm quiz và lưu tiến trình.', () => goQuiz('SECTION', sectionId))}>
                    ✏️ Quiz
                  </button>
                  <select className="select-inline" value={quizMode} onChange={(e) => setQuizMode(e.target.value)} aria-label="Dạng quiz">
                    {QUIZ_MODES.map((m) => <option key={m.value} value={m.value}>{m.label}</option>)}
                  </select>
                </span>
                <button type="button" className="btn btn-outline btn-sm"
                  onClick={() => requireLogin('Đăng nhập để luyện gõ phiên âm và lưu tiến trình.', () => goTyping('SECTION', sectionId))}>
                  ⌨️ Gõ phiên âm
                </button>
              </div>
            )}
          </div>
          )}

          {/* R2: thanh luyện tập thống nhất — hiện khi đã chọn từ (tick lẻ và/hoặc cụm). */}
          {selectionWords.length > 0 && (
            <div className="selection-bar">
              <span className="selection-count">Đã chọn <strong>{selectionWords.length}</strong> từ</span>
              <div className="selection-actions">
                <span className="practice-pair">
                  <button type="button" className="btn btn-primary btn-sm"
                    onClick={() => requireLogin('Đăng nhập để luyện flashcard và lưu tiến trình.', () => startSelectionPractice('flashcard'))}>
                    🃏 Flashcard
                  </button>
                  <select className="select-inline" value={flashcardDir} onChange={(e) => setFlashcardDir(e.target.value)} aria-label="Hướng flashcard">
                    {FLASHCARD_DIRS.map((m) => <option key={m.value} value={m.value}>{m.label}</option>)}
                  </select>
                </span>
                <span className="practice-pair">
                  <button type="button" className="btn btn-outline btn-sm"
                    onClick={() => requireLogin('Đăng nhập để làm quiz và lưu tiến trình.', () => startSelectionPractice('quiz'))}>
                    ✏️ Quiz
                  </button>
                  <select className="select-inline" value={quizMode} onChange={(e) => setQuizMode(e.target.value)} aria-label="Dạng quiz">
                    {QUIZ_MODES.map((m) => <option key={m.value} value={m.value}>{m.label}</option>)}
                  </select>
                </span>
                <button type="button" className="btn btn-outline btn-sm"
                  onClick={() => requireLogin('Đăng nhập để luyện gõ phiên âm và lưu tiến trình.', () => startSelectionPractice('typing'))}>
                  ⌨️ Gõ phiên âm
                </button>
                <button type="button" className="btn btn-outline btn-sm"
                  onClick={() => requireLogin('Đăng nhập để thêm từ vào bộ thẻ của bạn.', () => setAddSetOpen(true))}>
                  ＋ Thêm vào bộ thẻ
                </button>
              </div>
            </div>
          )}

          {/* F-A: toggle chế độ xem Theo phần | Theo cụm */}
          <div className="tabs">
            <button type="button" className={`tab${viewMode === 'section' ? ' active' : ''}`}
              onClick={() => setViewMode('section')}>
              Theo phần
            </button>
            <button type="button" className={`tab${viewMode === 'cluster' ? ' active' : ''}`}
              onClick={() => setViewMode('cluster')}>
              Theo cụm chủ đề
            </button>
            <button type="button" className={`tab${viewMode === 'grammar' ? ' active' : ''}`}
              onClick={() => setViewMode('grammar')}>
              Ngữ pháp
            </button>
          </div>

          {viewMode === 'grammar' ? (
            <>
              {/* Ngữ pháp: view đọc — không có danh sách từ, không thanh luyện tập */}
              {grammarLoading ? (
                <p className="muted">Đang tải ngữ pháp…</p>
              ) : grammarError ? (
                <div className="alert alert-error">{grammarError}</div>
              ) : !grammarItems || grammarItems.length === 0 ? (
                <p className="muted">Bài này chưa có dữ liệu ngữ pháp.</p>
              ) : (
                <div className="grammar-list">
                  {grammarItems.map((p) => (
                    <GrammarPointCard
                      key={p.id}
                      point={p}
                      relatedTitles={grammarTitles}
                      onRelated={(id) => setRelatedId(id)}
                    />
                  ))}
                </div>
              )}
            </>
          ) : viewMode === 'section' ? (
            <>
              <div className="tabs tabs-sub">
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
          ) : (
            <>
              {/* F-A: chọn cụm chủ đề để luyện */}
              {loading ? (
                <p className="muted">Đang tải cụm chủ đề…</p>
              ) : clusters.length === 0 ? (
                <p className="muted">Bài này chưa có cụm chủ đề nào.</p>
              ) : (
                <>
                  <div className="cluster-panel">
                    <div className="cluster-panel-head">
                      <span className="muted">Tick chọn cụm để luyện (chưa chọn → xem toàn bộ):</span>
                    </div>
                    <div className="cluster-grid">
                      {clusters.map((c) => (
                        <label key={c.cluster} className={`cluster-item${selectedClusters[c.cluster] ? ' selected' : ''}`}>
                          <input type="checkbox" checked={!!selectedClusters[c.cluster]}
                            onChange={() => toggleCluster(c.cluster)} />
                          <span className="cluster-name">{c.label || clusterLabel(c.cluster)}</span>
                          <span className="cluster-count">{c.count} từ</span>
                        </label>
                      ))}
                    </div>
                  </div>

                  {/* Tick từng từ để thêm vào bộ thẻ (chế độ cụm vẫn giữ được chọn lẻ). */}
                  <div className="toolbar">
                    <span className="muted">Tick từng từ rồi thêm vào bộ thẻ · đã chọn {selectedWords.length}</span>
                    {selectedWords.length > 0 && (
                      <button type="button" className="btn btn-primary btn-sm"
                        onClick={() => requireLogin('Đăng nhập để thêm từ vào bộ thẻ của bạn.', () => setAddSetOpen(true))}>
                        ＋ Thêm {selectedWords.length} từ vào bộ thẻ
                      </button>
                    )}
                  </div>

                  {visibleClusters.map((c) => {
                    const ws = wordsByCluster[c.cluster] || [];
                    if (ws.length === 0) return null;
                    return (
                      <div key={c.cluster} className="cluster-section">
                        <h3 className="cluster-section-title">
                          {c.label || clusterLabel(c.cluster)}
                          <span className="muted"> · {ws.length} từ</span>
                        </h3>
                        <div className="word-list">
                          {ws.map((w) => (
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
                      </div>
                    );
                  })}
                </>
              )}
            </>
          )}
        </>
      )}
      <GrammarRelatedModal
        grammarId={relatedId}
        relatedTitles={grammarTitles}
        onClose={() => setRelatedId(null)}
        onRelated={(id) => setRelatedId(id)}
      />
      <LoginPrompt open={!!loginPrompt} onClose={() => setLoginPrompt('')} message={loginPrompt} />
      <AddToSetModal open={addSetOpen} onClose={() => setAddSetOpen(false)} words={selectedWords} />
    </div>
  );
}
