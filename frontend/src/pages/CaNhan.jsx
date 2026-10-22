import { useEffect, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import Dashboard from './Dashboard.jsx';
import Sets from './Sets.jsx';
import SetDetail from './SetDetail.jsx';
import { fetchProgressSummary } from '../api/study.js';
import { getErrorMessage } from '../api/client.js';
import { useAuth } from '../context/AuthContext.jsx';
import { GearIcon, TrophyIcon } from '../components/icons.jsx';
import './pages.css';

// v2.0 (A3): tab Cá nhân (/ca-nhan) — 4 tab con:
// Tổng quan (reuse Dashboard) | Bộ thẻ của tôi (reuse Sets/SetDetail)
// | Thành tích (UI tĩnh từ progress hiện có) | Cài đặt.
// Route /ca-nhan/bo-the/:id → tab Bộ thẻ + chi tiết bộ (SetDetail đọc id từ URL).
export default function CaNhan({ initialTab }) {
  const [searchParams] = useSearchParams();
  const { id } = useParams();
  const { user, logout } = useAuth();
  const [tab, setTab] = useState(initialTab || searchParams.get('tab') || 'tong-quan');

  const TABS = [
    { value: 'tong-quan', label: 'Tổng quan' },
    { value: 'bo-the', label: 'Bộ thẻ của tôi' },
    { value: 'thanh-tich', label: 'Thành tích' },
    { value: 'cai-dat', label: 'Cài đặt' },
  ];

  return (
    <div className="page">
      <div className="canhan-hello">
        <span className="jp-badge" style={{ width: 52, height: 52, fontSize: 26, borderRadius: 16 }}>個</span>
        <div>
          <h1>Cá nhân</h1>
          <p className="muted" style={{ margin: 0 }}>Chào {user?.name || 'bạn'}! Tiến trình, bộ thẻ và cài đặt của bạn đều ở đây.</p>
        </div>
      </div>

      <div className="canhan-tabs" role="tablist">
        {TABS.map((t) => (
          <button
            key={t.value}
            type="button"
            role="tab"
            aria-selected={tab === t.value}
            className={`hub-tab${tab === t.value ? ' active' : ''}`}
            onClick={() => setTab(t.value)}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'tong-quan' && <Dashboard />}

      {tab === 'bo-the' && (
        id ? <SetDetail key={id} /> : <Sets />
      )}

      {tab === 'thanh-tich' && <Achievements />}

      {tab === 'cai-dat' && <Settings user={user} onLogout={logout} />}
    </div>
  );
}

// Thành tích — huy hiệu tĩnh suy ra từ progress hiện có (không API mới).
function Achievements() {
  const [summary, setSummary] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    fetchProgressSummary()
      .then(setSummary)
      .catch((e) => setError(getErrorMessage(e, 'Không tải được tiến trình.')))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <p className="muted">Đang tải thành tích…</p>;
  if (error) return <div className="alert alert-error">{error}</div>;
  if (!summary) return null;

  const byLevel = summary.byLevel || {};
  const masteredTotal = ['N5', 'N4'].reduce((a, lv) => a + (byLevel[lv]?.mastered || 0), 0);
  const lessons = summary.lessons || [];
  const doneLessons = lessons.filter((l) => (l.total || 0) > 0 && (l.mastered || 0) >= (l.total || 0)).length;
  const sessions = (summary.recentSessions || []).length;
  const streak = summary.streakDays || 0;

  const ACH = [
    { badge: '始', name: 'Khởi động', desc: 'Hoàn thành 1 phiên học', got: sessions > 0 },
    { badge: '語', name: 'Vốn từ 100', desc: 'Thuộc 100 từ vựng', got: masteredTotal >= 100 },
    { badge: '語', name: 'Vốn từ 500', desc: 'Thuộc 500 từ vựng', got: masteredTotal >= 500 },
    { badge: '復', name: 'Tuần rực lửa', desc: 'Chuỗi 7 ngày liên tiếp', got: streak >= 7 },
    { badge: '復', name: 'Tháng bền bỉ', desc: 'Chuỗi 30 ngày liên tiếp', got: streak >= 30 },
    { badge: '文', name: 'Chạy đà', desc: 'Hoàn thành 5 bài học', got: doneLessons >= 5 },
    { badge: '文', name: 'Nửa chặng N5', desc: 'Hoàn thành 25 bài học', got: doneLessons >= 25 },
    { badge: '漢', name: 'Chinh phục 50', desc: 'Hoàn thành cả 50 bài', got: doneLessons >= 50 },
  ];
  const gotCount = ACH.filter((a) => a.got).length;

  return (
    <div>
      <p className="muted">
        <TrophyIcon size={16} /> Bạn đã đạt {gotCount}/{ACH.length} huy hiệu — cố lên!
      </p>
      <div className="ach-grid">
        {ACH.map((a, i) => (
          <div key={i} className={`ach-card${a.got ? ' got' : ' locked'}`}>
            <span className={`jp-badge${a.got ? ' gold' : ''}`}>{a.badge}</span>
            <p>{a.name}</p>
            <small>{a.desc}</small>
          </div>
        ))}
      </div>
    </div>
  );
}

// Cài đặt — thông tin tài khoản + link tới các trang hiện có.
function Settings({ user, onLogout }) {
  return (
    <div className="settings-card">
      <h3 style={{ marginTop: 0, display: 'flex', alignItems: 'center', gap: 8 }}>
        <GearIcon size={20} /> Cài đặt tài khoản
      </h3>
      <div className="settings-row">
        <span className="muted">Tên hiển thị</span>
        <strong>{user?.name || '—'}</strong>
      </div>
      <div className="settings-row">
        <span className="muted">Email</span>
        <strong>{user?.email || '—'}</strong>
      </div>
      <div className="settings-row">
        <span className="muted">Mật khẩu</span>
        <Link to="/quen-mat-khau" className="btn btn-sm btn-outline">Đổi mật khẩu</Link>
      </div>
      <div className="settings-row">
        <span className="muted">Phiên đăng nhập</span>
        <button type="button" className="btn btn-sm btn-outline" onClick={onLogout}>Đăng xuất</button>
      </div>
    </div>
  );
}
