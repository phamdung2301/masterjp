import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { fetchLessons } from '../api/lessons.js';
import { getErrorMessage } from '../api/client.js';
import { useAuth } from '../context/AuthContext.jsx';
import './pages.css';

// US-003 (F-010): danh sách bài theo tab N5/N4, % hoàn thành của user (nếu login).
export default function Lessons() {
  const { isLoggedIn } = useAuth();
  const [level, setLevel] = useState('N5');
  const [lessons, setLessons] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    setLoading(true);
    setError('');
    fetchLessons(level)
      .then(setLessons)
      .catch((e) => setError(getErrorMessage(e, 'Không tải được danh sách bài học.')))
      .finally(() => setLoading(false));
  }, [level]);

  const pct = (l) => (l.totalWords > 0 ? Math.round((l.masteredWords / l.totalWords) * 100) : 0);

  return (
    <div className="page">
      <h1>Bài học</h1>
      <p className="muted">50 bài theo giáo trình Minna no Nihongo — quyển I (N5) và quyển II (N4).</p>
      <div className="tabs">
        {['N5', 'N4'].map((lv) => (
          <button key={lv} type="button" className={`tab${level === lv ? ' active' : ''}`} onClick={() => setLevel(lv)}>
            {lv} — {lv === 'N5' ? 'Bài 1–25' : 'Bài 26–50'}
          </button>
        ))}
      </div>
      {error && <div className="alert alert-error">{error}</div>}
      {loading ? (
        <p className="muted">Đang tải…</p>
      ) : (
        <div className="lesson-grid">
          {lessons.map((l) => (
            <Link key={l.id} to={`/bai-hoc/${l.id}`} className="lesson-card">
              <div className="lesson-no">Bài {l.lessonNo}</div>
              <div className="lesson-ja">{l.titleJa}</div>
              <div className="lesson-vi">{l.titleVi}</div>
              <div className="lesson-meta">{l.totalWords} từ</div>
              {isLoggedIn && (
                <div className="progress-bar" title={`${l.masteredWords}/${l.totalWords} từ đã thuộc`}>
                  <div className="progress-fill" style={{ width: `${pct(l)}%` }} />
                  <span className="progress-label">{pct(l)}%</span>
                </div>
              )}
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
