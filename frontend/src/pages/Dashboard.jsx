import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { fetchProgressSummary } from '../api/study.js';
import { getErrorMessage } from '../api/client.js';
import { useAuth } from '../context/AuthContext.jsx';
import { formatDateTime } from '../utils.js';
import { FlameIcon } from '../components/icons.jsx';
import './pages.css';

// US-009 (F-028, F-029): dashboard — streak, NEW/LEARNING/MASTERED theo N5/N4,
// % hoàn thành từng bài, 10 phiên học gần nhất. Cần login.
const MODE_LABELS = {
  FLASHCARD: 'Flashcard',
  QUIZ_JP_VI: 'Quiz — Nhật → Việt',
  QUIZ_VI_JP: 'Quiz — Việt → Nhật',
  LISTENING: 'Quiz — Luyện nghe',
  TYPING: 'Quiz — Gõ từ',
  REVIEW_DUE: 'Ôn tập đến hạn',
};

export default function Dashboard() {
  const { user } = useAuth();
  const [summary, setSummary] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    fetchProgressSummary()
      .then(setSummary)
      .catch((e) => setError(getErrorMessage(e, 'Không tải được tiến trình.')))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="page"><p className="muted">Đang tải…</p></div>;
  if (error) return <div className="page"><div className="alert alert-error">{error}</div></div>;
  if (!summary) return null;

  const byLevel = summary.byLevel || {};
  const lessons = summary.lessons || [];
  const recentSessions = summary.recentSessions || [];
  const streakDays = summary.streakDays ?? user?.streakDays ?? 0;

  // ⚠️ JSON BE trả keys lowercase: { total, new, learning, mastered } (record LevelProgressDto).
  const levelStat = (lv) => {
    const s = byLevel[lv] || {};
    const total = (s.new || 0) + (s.learning || 0) + (s.mastered || 0);
    return { newCount: s.new || 0, learning: s.learning || 0, mastered: s.mastered || 0, total };
  };

  return (
    <div className="page">
      <h1>Tổng quan</h1>
      <p className="muted">Chào {user?.name || 'bạn'}! Đây là tiến trình học của bạn.</p>

      <div className="stat-grid">
        <div className="stat-card highlight">
          <div className="stat-value"><FlameIcon size={22} /> {streakDays}</div>
          <div className="stat-label">ngày học liên tiếp</div>
        </div>
        {['N5', 'N4'].map((lv) => {
          const s = levelStat(lv);
          return (
            <div key={lv} className="stat-card">
              <div className="stat-value">{s.mastered || 0}<span className="stat-sub">/{s.total}</span></div>
              <div className="stat-label">{lv} — từ đã thuộc</div>
              <div className="stat-detail">
                <span className="dot-new" /> Mới: {s.newCount || 0}
                {' · '}
                <span className="dot-learning" /> Đang học: {s.learning || 0}
              </div>
            </div>
          );
        })}
      </div>

      <div className="dashboard-actions">
        <Link to="/on-tap" className="btn btn-primary">Ôn tập đến hạn</Link>
        <Link to="/hoc" className="btn btn-outline">Học bài mới</Link>
      </div>

      <h2>Tiến độ từng bài</h2>
      {lessons.length === 0 ? (
        <p className="muted">Bạn chưa học bài nào — <Link to="/hoc">bắt đầu bài đầu tiên</Link> nhé!</p>
      ) : (
        <div className="lesson-progress-list">
          {lessons.map((l) => {
            // LessonProgressDto: { lessonId, lessonNo, level, titleJa, titleVi, total, mastered }.
            const total = l.total || 0;
            const pct = total > 0 ? Math.round(((l.mastered || 0) / total) * 100) : 0;
            return (
              <Link key={l.lessonId || l.id} to={`/hoc/bai/${l.lessonId || l.id}`} className="lesson-progress-row">
                <span className="lp-no">Bài {l.lessonNo}</span>
                <span className="lp-title">{l.titleVi || l.titleJa}</span>
                <div className="progress-bar"><div className="progress-fill" style={{ width: `${pct}%` }} /></div>
                <span className="lp-pct">{pct}%</span>
              </Link>
            );
          })}
        </div>
      )}

      <h2>Phiên học gần đây</h2>
      {recentSessions.length === 0 ? (
        <p className="muted">Chưa có phiên học nào.</p>
      ) : (
        <div className="session-list">
          {recentSessions.slice(0, 10).map((s, i) => (
            <div key={s.id || i} className="session-row">
              <span className="session-mode">{MODE_LABELS[s.mode] || s.mode}</span>
              <span>{s.correctCount}/{s.totalItems} đúng</span>
              <span className="muted">{formatDateTime(s.finishedAt || s.startedAt)}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
