import { useEffect, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import Dashboard from './Dashboard.jsx';
import Sets from './Sets.jsx';
import SetDetail from './SetDetail.jsx';
import { getErrorMessage } from '../api/client.js';
import { useAuth } from '../context/AuthContext.jsx';
import { GearIcon, TrophyIcon, CrownIcon } from '../components/icons.jsx';
import { fetchBadges, fetchHeatmap } from '../api/gamification.js';
import BadgesGrid from '../components/gamification/BadgesGrid.jsx';
import Heatmap from '../components/gamification/Heatmap.jsx';
import LeaderboardPanel from '../components/gamification/LeaderboardPanel.jsx';
import MyInvoices from '../components/MyInvoices.jsx';
import { updateMySettings } from '../api/auth.js';
import { formatDate } from '../utils.js';
import './pages.css';
import './nangcap.css';

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
    { value: 'premium', label: 'Premium' },
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

      {tab === 'premium' && <PremiumTab user={user} />}

      {tab === 'cai-dat' && <Settings user={user} onLogout={logout} />}
    </div>
  );
}

// Thành tích (F-042) — huy hieu that tu GET /api/gamification/badges
// + heatmap 12 tuan + bang xep hang tuan. Khong con badge hard-code.
function Achievements() {
  const [badges, setBadges] = useState(null);
  const [heatmap, setHeatmap] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    Promise.all([fetchBadges(), fetchHeatmap(12)])
      .then(([b, h]) => {
        if (cancelled) return;
        setBadges(b);
        setHeatmap(h);
      })
      .catch((e) => {
        if (!cancelled) setError(getErrorMessage(e, 'Không tải được thành tích.'));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  if (loading) return <p className="muted">Đang tải thành tích…</p>;
  if (error) return <div className="alert alert-error">{error}</div>;

  const earnedCount = badges?.earnedCount || 0;
  const totalCount = badges?.totalCount || 0;

  return (
    <div className="achieve-wrap">
      <p className="muted">
        <TrophyIcon size={16} /> Bạn đã đạt {earnedCount}/{totalCount} huy hiệu — cố lên!
      </p>
      <BadgesGrid badges={badges?.badges} />
      <Heatmap days={heatmap?.days} weeks={heatmap?.weeks} />
      <LeaderboardPanel />
    </div>
  );
}

// Tab Premium (F-044): trạng thái gói hiện tại + nút nâng cấp + hóa đơn của tôi.
function PremiumTab({ user }) {
  const isPremium = user?.plan === 'PREMIUM';
  return (
    <div>
      <div className="settings-card" style={{ marginBottom: 20 }}>
        <h3 style={{ marginTop: 0, display: 'flex', alignItems: 'center', gap: 8 }}>
          <CrownIcon size={20} /> Gói của bạn
        </h3>
        {isPremium ? (
          <p style={{ margin: 0 }}>
            Bạn đang dùng <strong>Premium</strong>
            {user?.planExpiresAt && <> — có hiệu lực đến <strong>{formatDate(user.planExpiresAt)}</strong></>}.
            Cảm ơn bạn đã đồng hành cùng Tanu-chan!
          </p>
        ) : (
          <p style={{ margin: '0 0 12px' }}>
            Bạn đang dùng gói <strong>Free</strong> (20 từ mới/ngày). Nâng cấp để học không giới hạn —
            chỉ từ <strong>49.000đ/tháng</strong>.
          </p>
        )}
        <Link to="/ca-nhan/nang-cap" className="btn btn-primary btn-sm">
          <CrownIcon size={16} /> {isPremium ? 'Gia hạn Premium' : 'Nâng cấp Premium'}
        </Link>
      </div>
      <h3 style={{ color: 'var(--washi-ink)' }}>Hóa đơn của tôi</h3>
      <MyInvoices />
    </div>
  );
}

// Cài đặt — thông tin tài khoản + link tới các trang hiện có.
function Settings({ user, onLogout }) {
  const { refreshUser } = useAuth();
  // F-046: opt-in email nhắc ôn tập (default true; user cũ null = true — DOC-13 §5.6).
  const [reminderOptIn, setReminderOptIn] = useState(user?.emailReminderOptIn !== false);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState('');

  const toggleReminder = async () => {
    const next = !reminderOptIn;
    setReminderOptIn(next); // lạc quan — hoàn tác nếu lưu lỗi
    setSaving(true);
    setSaveError('');
    try {
      await updateMySettings({ emailReminderOptIn: next });
      await refreshUser();
    } catch (e) {
      setReminderOptIn(!next);
      setSaveError(getErrorMessage(e, 'Không lưu được cài đặt, vui lòng thử lại.'));
    } finally {
      setSaving(false);
    }
  };

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
        <div>
          <div><strong>Email nhắc ôn tập</strong></div>
          <div className="muted" style={{ fontSize: '0.85rem' }}>
            Tanu-chan nhắc bạn lúc 8:00 sáng những hôm có từ đến hạn.
          </div>
        </div>
        <button
          type="button"
          role="switch"
          aria-checked={reminderOptIn}
          aria-label="Nhận email nhắc ôn tập"
          className={`toggle${reminderOptIn ? ' on' : ''}`}
          onClick={toggleReminder}
          disabled={saving}
        >
          <span className="toggle-knob" />
        </button>
      </div>
      {saveError && <div className="alert alert-error">{saveError}</div>}
      <div className="settings-row">
        <span className="muted">Phiên đăng nhập</span>
        <button type="button" className="btn btn-sm btn-outline" onClick={onLogout}>Đăng xuất</button>
      </div>
    </div>
  );
}
