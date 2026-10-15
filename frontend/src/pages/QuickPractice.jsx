import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { fetchLessonClusters, fetchLessonWords, fetchLessons } from '../api/lessons.js';
import { getErrorMessage } from '../api/client.js';
import { QUIZ_MODES, clusterLabel } from '../utils.js';
import './pages.css';

// F-C: luyện tập nhanh — chọn nhiều bài (checkbox, nhóm N5/N4) + chọn cụm trong
// từng bài đã chọn. Hiện tổng số từ realtime. Bắt đầu → POST /api/quiz/quick
// {wordIds, mode} qua trang Quiz tái dùng (quick=1). KHÔNG tạo bộ thẻ.
const QUICK_KEY = 'mjp_quick_quiz';
const MIN_WORDS = 4;
const MAX_WORDS = 100;

export default function QuickPractice() {
  const navigate = useNavigate();
  const [params] = useSearchParams();

  const [lessons, setLessons] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [selectedLessons, setSelectedLessons] = useState({}); // lessonId → true
  const [lessonClusters, setLessonClusters] = useState({}); // lessonId → { loading, clusters }
  const [selectedClusters, setSelectedClusters] = useState({}); // `${lessonId}:${cluster}` → true
  const [mode, setMode] = useState('JP_VI');
  const [starting, setStarting] = useState(false);
  const [startError, setStartError] = useState('');

  // Load danh sách 50 bài (API yêu cầu level bắt buộc → gọi cả N5 và N4).
  useEffect(() => {
    Promise.all([fetchLessons('N5'), fetchLessons('N4')])
      .then(([n5, n4]) => setLessons([...(n5 || []), ...(n4 || [])]))
      .catch((e) => setError(getErrorMessage(e, 'Không tải được danh sách bài học.')))
      .finally(() => setLoading(false));
  }, []);

  // Preselect từ LessonDetail (chế độ cụm): ?lessonId=..&clusters=a,b
  const [presetApplied, setPresetApplied] = useState(false);
  useEffect(() => {
    if (presetApplied || loading || lessons.length === 0) return;
    const lessonId = params.get('lessonId');
    const clusterParam = params.get('clusters');
    if (!lessonId) { setPresetApplied(true); return; }
    if (!lessons.some((l) => l.id === lessonId)) { setPresetApplied(true); return; }
    setSelectedLessons({ [lessonId]: true });
    ensureClusters(lessonId).then((clusters) => {
      const wanted = clusterParam ? clusterParam.split(',').filter(Boolean) : null;
      const valid = clusters.filter((c) => !wanted || wanted.includes(c.cluster));
      setSelectedClusters((prev) => {
        const next = { ...prev };
        for (const c of valid) next[`${lessonId}:${c.cluster}`] = true;
        return next;
      });
      setPresetApplied(true);
    }).catch(() => setPresetApplied(true));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loading, lessons, presetApplied]);

  const ensureClusters = async (lessonId) => {
    const cached = lessonClusters[lessonId];
    if (cached && cached.clusters) return cached.clusters;
    setLessonClusters((prev) => ({ ...prev, [lessonId]: { loading: true, clusters: [] } }));
    const clusters = await fetchLessonClusters(lessonId);
    setLessonClusters((prev) => ({ ...prev, [lessonId]: { loading: false, clusters: clusters || [] } }));
    return clusters || [];
  };

  const toggleLesson = async (lessonId) => {
    const isOn = !selectedLessons[lessonId];
    if (isOn) {
      setSelectedLessons((prev) => ({ ...prev, [lessonId]: true }));
      try {
        const clusters = await ensureClusters(lessonId);
        setSelectedClusters((prev) => {
          const next = { ...prev };
          for (const c of clusters) next[`${lessonId}:${c.cluster}`] = true;
          return next;
        });
      } catch (e) {
        setError(getErrorMessage(e, 'Không tải được cụm của bài.'));
      }
    } else {
      setSelectedLessons((prev) => {
        const next = { ...prev };
        delete next[lessonId];
        return next;
      });
      setSelectedClusters((prev) => {
        const next = { ...prev };
        for (const k of Object.keys(next)) {
          if (k.startsWith(`${lessonId}:`)) delete next[k];
        }
        return next;
      });
    }
  };

  const toggleCluster = (lessonId, cluster) => {
    const key = `${lessonId}:${cluster}`;
    setSelectedClusters((prev) => {
      const next = { ...prev };
      if (next[key]) delete next[key];
      else next[key] = true;
      return next;
    });
  };

  const groups = useMemo(() => {
    const n5 = lessons.filter((l) => l.level === 'N5' || l.lessonNo <= 25);
    const n4 = lessons.filter((l) => !(l.level === 'N5' || l.lessonNo <= 25));
    return [{ level: 'N5', lessons: n5 }, { level: 'N4', lessons: n4 }];
  }, [lessons]);

  // Tổng số từ realtime = cộng count của các cụm đã chọn.
  const totalWords = useMemo(() => {
    let sum = 0;
    for (const key of Object.keys(selectedClusters)) {
      const sep = key.lastIndexOf(':');
      const lessonId = key.slice(0, sep);
      const cluster = key.slice(sep + 1);
      const info = lessonClusters[lessonId];
      const c = info?.clusters?.find((x) => x.cluster === cluster);
      if (c) sum += c.count;
    }
    return sum;
  }, [selectedClusters, lessonClusters]);

  const selectedLessonIds = Object.keys(selectedLessons);
  const canStart = totalWords >= MIN_WORDS && totalWords <= MAX_WORDS && !starting;

  const handleStart = async () => {
    setStartError('');
    if (totalWords < MIN_WORDS) {
      setStartError(`Cần ít nhất ${MIN_WORDS} từ để tạo quiz. Hãy chọn thêm bài hoặc cụm.`);
      return;
    }
    if (totalWords > MAX_WORDS) {
      setStartError(`Tối đa ${MAX_WORDS} từ mỗi lần luyện. Hãy bỏ bớt bài hoặc cụm.`);
      return;
    }
    setStarting(true);
    try {
      // Lấy wordIds thật: 1 request / bài rồi lọc theo cụm đã chọn (field cluster của WordDto).
      const perLesson = await Promise.all(
        selectedLessonIds.map(async (lessonId) => {
          const codes = new Set(
            Object.keys(selectedClusters)
              .filter((k) => k.startsWith(`${lessonId}:`))
              .map((k) => k.slice(lessonId.length + 1))
          );
          if (codes.size === 0) return [];
          const ws = await fetchLessonWords(lessonId);
          return ws
            .filter((w) => codes.has(w.cluster || 'khac'))
            .sort((a, b) => (a.orderInLesson ?? 0) - (b.orderInLesson ?? 0))
            .map((w) => w.id);
        })
      );
      const wordIds = [...new Set(perLesson.flat())];
      if (wordIds.length < MIN_WORDS) {
        throw new Error('Không đủ từ sau khi lọc. Hãy chọn thêm.');
      }
      const limited = wordIds.slice(0, MAX_WORDS);
      sessionStorage.setItem(QUICK_KEY, JSON.stringify({
        wordIds: limited,
        mode,
        lessonCount: selectedLessonIds.length,
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

  if (loading) return <div className="page"><p className="muted">Đang tải…</p></div>;

  return (
    <div className="page">
      <h1>⚡ Luyện tập nhanh</h1>
      <p className="muted">
        Chọn nhiều bài và cụm chủ đề, làm quiz ngay mà không cần tạo bộ thẻ.
        Tổng số từ cập nhật theo thời gian thực.
      </p>
      {error && <div className="alert alert-error">{error}</div>}

      <div className="quick-layout">
        <div className="quick-lessons">
          <h3>1. Chọn bài học</h3>
          {groups.map((g) => g.lessons.length > 0 && (
            <div key={g.level} className="quick-level-group">
              <div className="quick-level-title">Cấp độ {g.level}</div>
              <div className="quick-lesson-list">
                {g.lessons.map((l) => (
                  <label key={l.id} className={`quick-lesson-item${selectedLessons[l.id] ? ' selected' : ''}`}>
                    <input type="checkbox" checked={!!selectedLessons[l.id]}
                      onChange={() => toggleLesson(l.id)} />
                    <span className="quick-lesson-name">Bài {l.lessonNo} — {l.titleVi}</span>
                  </label>
                ))}
              </div>
            </div>
          ))}
        </div>

        <div className="quick-clusters">
          <h3>2. Chọn cụm chủ đề</h3>
          {selectedLessonIds.length === 0 ? (
            <p className="muted">Tick chọn bài ở cột bên trái, cụm của bài sẽ hiện ở đây.</p>
          ) : (
            selectedLessonIds.map((lessonId) => {
              const lesson = lessons.find((l) => l.id === lessonId);
              const info = lessonClusters[lessonId];
              return (
                <div key={lessonId} className="quick-lesson-clusters">
                  <div className="quick-lesson-clusters-title">
                    Bài {lesson?.lessonNo} — {lesson?.titleVi}
                  </div>
                  {!info || info.loading ? (
                    <p className="muted">Đang tải cụm…</p>
                  ) : info.clusters.length === 0 ? (
                    <p className="muted">Bài này chưa có cụm.</p>
                  ) : (
                    <div className="cluster-grid">
                      {info.clusters.map((c) => {
                        const key = `${lessonId}:${c.cluster}`;
                        return (
                          <label key={c.cluster} className={`cluster-item${selectedClusters[key] ? ' selected' : ''}`}>
                            <input type="checkbox" checked={!!selectedClusters[key]}
                              onChange={() => toggleCluster(lessonId, c.cluster)} />
                            <span className="cluster-name">{c.label || clusterLabel(c.cluster)}</span>
                            <span className="cluster-count">{c.count} từ</span>
                          </label>
                        );
                      })}
                    </div>
                  )}
                </div>
              );
            })
          )}
        </div>
      </div>

      <div className="quick-start-bar">
        <div className="quick-total">
          <span className="quick-total-num">{totalWords}</span>
          <span className="muted">từ sẽ luyện</span>
          {totalWords > MAX_WORDS && (
            <span className="alert-inline">Tối đa {MAX_WORDS} từ — hãy bỏ bớt.</span>
          )}
          {totalWords > 0 && totalWords < MIN_WORDS && (
            <span className="alert-inline">Cần ít nhất {MIN_WORDS} từ.</span>
          )}
        </div>
        <div className="quick-mode">
          <span className="muted">Dạng quiz:</span>
          <select className="select-inline" value={mode} onChange={(e) => setMode(e.target.value)} aria-label="Dạng quiz">
            {QUIZ_MODES.map((m) => <option key={m.value} value={m.value}>{m.label}</option>)}
          </select>
          <button type="button" className="btn btn-primary btn-lg" disabled={!canStart} onClick={handleStart}>
            {starting ? 'Đang chuẩn bị…' : '🚀 Bắt đầu luyện'}
          </button>
        </div>
      </div>
      {startError && <div className="alert alert-error">{startError}</div>}
    </div>
  );
}
