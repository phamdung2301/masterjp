import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { fetchLessons } from '../api/lessons.js';
import { getErrorMessage } from '../api/client.js';
import { useAuth } from '../context/AuthContext.jsx';
import { CheckIcon } from '../components/icons.jsx';
import './pages.css';

// v2.0 (A3): tab Học (/hoc) — lộ trình 50 bài Minna no Nihongo dạng grid,
// filter N5/N4, mỗi bài hiện tiến trình % (API lessons hiện có),
// nút "Học tiếp" → bài đang học dở (không thêm API mới).
export default function Hoc() {
  const { isLoggedIn } = useAuth();
  const [level, setLevel] = useState('N5');
  const [allLessons, setAllLessons] = useState([]); // N5+N4 — cho nút "Học tiếp"
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    setLoading(true);
    setError('');
    Promise.all([fetchLessons('N5'), fetchLessons('N4')])
      .then(([n5, n4]) => {
        setAllLessons([...(n5 || []), ...(n4 || [])]);
      })
      .catch((e) => setError(getErrorMessage(e, 'Không tải được danh sách bài học.')))
      .finally(() => setLoading(false));
  }, []);

  const lessons = useMemo(
    () => allLessons.filter((l) => l.level === level).sort((a, b) => a.lessonNo - b.lessonNo),
    [allLessons, level]
  );

  // Bài đang học dở: mastered > 0 nhưng chưa xong; nếu chưa có → bài đầu tiên chưa học.
  const continueLesson = useMemo(() => {
    if (!isLoggedIn || allLessons.length === 0) return null;
    const sorted = [...allLessons].sort((a, b) => a.lessonNo - b.lessonNo);
    const inProgress = sorted.find(
      (l) => l.totalWords > 0 && l.masteredWords > 0 && l.masteredWords < l.totalWords
    );
    if (inProgress) return inProgress;
    return sorted.find((l) => l.totalWords > 0 && l.masteredWords === 0) || null;
  }, [allLessons, isLoggedIn]);

  const allDone = useMemo(
    () =>
      isLoggedIn &&
      allLessons.length > 0 &&
      allLessons.every((l) => l.totalWords === 0 || l.masteredWords >= l.totalWords),
    [allLessons, isLoggedIn]
  );

  const pct = (l) => (l.totalWords > 0 ? Math.round((l.masteredWords / l.totalWords) * 100) : 0);

  return (
    <div className="page">
      <h1>Học theo lộ trình</h1>
      <p className="muted">50 bài theo giáo trình Minna no Nihongo — quyển I (N5) và quyển II (N4). Chọn một bài để mở trang chi tiết bài: từ vựng, cụm chủ đề, ngữ pháp, kanji và luyện tập trong cùng một trang.</p>

      {error && <div className="alert alert-error">{error}</div>}

      {loading ? (
        <p className="muted">Đang tải lộ trình…</p>
      ) : (
        <>
          {isLoggedIn && continueLesson && (
            <div className="hoc-continue">
              <div>
                <h2>Tiếp tục học: Bài {continueLesson.lessonNo}</h2>
                <p>{continueLesson.titleVi} · đã thuộc {continueLesson.masteredWords}/{continueLesson.totalWords} từ ({pct(continueLesson)}%)</p>
              </div>
              <Link to={`/hoc/bai/${continueLesson.id}`} className="btn btn-primary btn-lg">Học tiếp →</Link>
            </div>
          )}
          {allDone && (
            <div className="hoc-continue">
              <div>
                <h2>Tuyệt vời — bạn đã học hết 50 bài!</h2>
                <p>Hãy duy trì chuỗi ôn tập mỗi ngày để không quên từ nào.</p>
              </div>
              <Link to="/on-tap" className="btn btn-primary btn-lg">Ôn tập đến hạn</Link>
            </div>
          )}

          <div className="tabs">
            {['N5', 'N4'].map((lv) => (
              <button key={lv} type="button" className={`tab${level === lv ? ' active' : ''}`} onClick={() => setLevel(lv)}>
                {lv} — {lv === 'N5' ? 'Bài 1–25' : 'Bài 26–50'}
              </button>
            ))}
          </div>

          {lessons.length === 0 ? (
            <p className="muted">Chưa có bài học nào cho cấp độ này.</p>
          ) : (
            <div className="hoc-grid">
              {lessons.map((l) => {
                const p = pct(l);
                const done = isLoggedIn && l.totalWords > 0 && l.masteredWords >= l.totalWords;
                return (
                  <Link key={l.id} to={`/hoc/bai/${l.id}`} className={`hoc-card${done ? ' done' : ''}`}>
                    {done && (
                      <span className="hoc-done-badge" title="Đã hoàn thành">
                        <CheckIcon size={16} />
                      </span>
                    )}
                    <div className="lesson-no">Bài {l.lessonNo}</div>
                    <div className="lesson-ja">{l.titleJa}</div>
                    <div className="lesson-vi">{l.titleVi}</div>
                    <div className="lesson-meta">{l.totalWords} từ</div>
                    {isLoggedIn && (
                      <div className="progress-bar" title={`${l.masteredWords}/${l.totalWords} từ đã thuộc`}>
                        <div className="progress-fill" style={{ width: `${p}%` }} />
                        <span className="progress-label">{p}%</span>
                      </div>
                    )}
                  </Link>
                );
              })}
            </div>
          )}
        </>
      )}
    </div>
  );
}
