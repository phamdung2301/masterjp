import { useEffect, useState } from 'react';
import { adminFetchStats } from '../../api/admin.js';
import { getErrorMessage } from '../../api/client.js';

// US-012 (F-034): thống kê tổng hợp — không xem dữ liệu cá nhân chi tiết của học viên.
export default function AdminOverview() {
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    adminFetchStats()
      .then(setStats)
      .catch((e) => setError(getErrorMessage(e, 'Không tải được thống kê.')))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <p className="muted">Đang tải…</p>;
  if (error) return <div className="alert alert-error">{error}</div>;
  if (!stats) return null;

  return (
    <div>
      <h2>Tổng quan</h2>
      <div className="stat-grid">
        <div className="stat-card"><div className="stat-value">{stats.totalUsers ?? '—'}</div><div className="stat-label">Người dùng</div></div>
        <div className="stat-card"><div className="stat-value">{stats.totalLessons ?? '—'}</div><div className="stat-label">Bài học</div></div>
        <div className="stat-card"><div className="stat-value">{stats.totalWords ?? '—'}</div><div className="stat-label">Từ vựng</div></div>
        <div className="stat-card"><div className="stat-value">{stats.totalSessions ?? '—'}</div><div className="stat-label">Tổng phiên học</div></div>
        <div className="stat-card"><div className="stat-value">{stats.sessionsToday ?? '—'}</div><div className="stat-label">Phiên học hôm nay</div></div>
      </div>

      {stats.topWrongWords?.length > 0 && (
        <>
          <h3>Từ bị sai nhiều nhất</h3>
          <p className="muted">Ưu tiên kiểm tra lại nghĩa / câu ví dụ của các từ này.</p>
          <div className="admin-table-wrap">
            <table className="admin-table">
              <thead><tr><th>Từ</th><th>Nghĩa</th><th>Bài</th><th>Lượt sai</th></tr></thead>
              <tbody>
                {stats.topWrongWords.map((w, i) => (
                  <tr key={w.wordId || i}>
                    <td>{w.kanji || w.kana} <span className="muted">{w.romaji}</span></td>
                    <td>{w.meaningVi}</td>
                    <td>{w.lessonNo ? `Bài ${w.lessonNo}` : '—'}</td>
                    <td>{w.wrongCount}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}

      {stats.sessionsByDay?.length > 0 && (
        <>
          <h3>Phiên học theo ngày (gần đây)</h3>
          <div className="admin-table-wrap">
            <table className="admin-table">
              <thead><tr><th>Ngày</th><th>Số phiên</th></tr></thead>
              <tbody>
                {stats.sessionsByDay.map((d, i) => (
                  <tr key={i}><td>{d.date}</td><td>{d.count}</td></tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  );
}
