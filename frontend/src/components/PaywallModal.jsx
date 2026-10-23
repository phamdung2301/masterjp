import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';
import { CrossIcon, CrownIcon } from './icons.jsx';
import './PaywallModal.css';

// F-045 — PaywallModal phong cách washi. Mở bởi interceptor toàn cục trong
// api/client.js khi backend trả 429 FREEMIUM_LIMIT (hoặc DAILY_LIMIT từ AI quota),
// thay vì toast lỗi thô. Copy trung thực, không tạo urgency giả (không dark pattern).
//
// Event: window.dispatchEvent(new CustomEvent('mjp:open-paywall', { detail: { code, limit, resetAt } }))
// - FREEMIUM_LIMIT: payload { limit, resetAt } (DOC-13 §7).
// - DAILY_LIMIT: quota AI Phase B (giữ nguyên message), chỉ đổi dòng tiêu đề.

function freemiumTitle(limit) {
  return `「Hôm nay bạn đã học đủ ${limit || 20} từ mới rồi!」`;
}

function freemiumBody(info) {
  const time = formatResetTime(info.resetAt);
  return (
    <>
      Tanu-chan thấy bạn chăm chỉ lắm đó! Gói Free cho phép học {info.limit || 20} từ mới
      mỗi ngày — từ mới sẽ mở lại lúc {time}. Ôn tập từ đã học thì vẫn thoải mái, không giới hạn.
    </>
  );
}

function formatResetTime(resetAt) {
  if (!resetAt) return '00:00';
  const d = new Date(resetAt);
  if (Number.isNaN(d.getTime())) return '00:00';
  return d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
}

function PaywallModal({ info, onClose }) {
  const navigate = useNavigate();
  const { isLoggedIn } = useAuth();
  const isFreemium = info.code !== 'DAILY_LIMIT';

  // ESC đóng modal; click nền không đóng (tránh bấm nhầm mất paywall giữa phiên học).
  useEffect(() => {
    const onKey = (e) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  const goUpgrade = () => {
    onClose();
    navigate(isLoggedIn ? '/ca-nhan/nang-cap' : '/dang-nhap');
  };

  return (
    <div className="paywall-overlay" role="dialog" aria-modal="true" aria-label="Nâng cấp Premium">
      <div className="paywall-card">
        <button type="button" className="paywall-close" onClick={onClose} aria-label="Đóng">
          <CrossIcon size={18} />
        </button>
        <div className="paywall-mascot" aria-hidden="true">
          <span className="jp-badge" style={{ width: 56, height: 56, fontSize: 28, borderRadius: 18 }}>狐</span>
        </div>
        <h2 className="paywall-title">
          {isFreemium ? freemiumTitle(info.limit) : '「Hôm nay Sensei AI đã giúp bạn khá nhiều rồi!」'}
        </h2>
        <p className="paywall-body">
          {isFreemium
            ? freemiumBody(info)
            : 'Lượt hỏi Sensei AI hôm nay đã hết — mai mình lại hỏi tiếp nhé. Nâng cấp Premium để được ưu tiên dùng AI nhiều hơn.'}
        </p>

        <div className="paywall-plans">
          <div className="paywall-plan">
            <div className="paywall-plan-name">Premium tháng</div>
            <div className="paywall-plan-price">49.000đ<span>/tháng</span></div>
            <div className="paywall-plan-note">Học không giới hạn · hủy lúc nào cũng được</div>
          </div>
          <div className="paywall-plan hot">
            <div className="paywall-plan-tag">TIẾT KIỆM 34%</div>
            <div className="paywall-plan-name">Premium năm</div>
            <div className="paywall-plan-price">390.000đ<span>/năm</span></div>
            <div className="paywall-plan-note">Mọi quyền lợi Premium · huy hiệu Người ủng hộ</div>
          </div>
        </div>

        <button type="button" className="btn btn-primary btn-lg btn-block" onClick={goUpgrade}>
          <CrownIcon size={18} /> Nâng cấp Premium
        </button>
        <button type="button" className="btn btn-ghost btn-block" onClick={onClose}>
          {isFreemium ? 'Để mai học tiếp' : 'Để mai hỏi tiếp'}
        </button>
        <p className="paywall-fineprint">
          Thanh toán chuyển khoản ngân hàng, kích hoạt tự động trong vài phút. Không gia hạn âm thầm —
          hết hạn Premium thì tài khoản quay về gói Free, dữ liệu học của bạn vẫn nguyên.
        </p>
      </div>
    </div>
  );
}

// Host đặt 1 lần trong App.jsx (trong AuthProvider + Router). Nghe event toàn cục
// từ interceptor — bất kỳ API nào trả 429 FREEMIUM_LIMIT/DAILY_LIMIT đều mở modal.
export function PaywallModalHost() {
  const [info, setInfo] = useState(null);

  useEffect(() => {
    const onOpen = (e) => setInfo(e.detail || {});
    window.addEventListener('mjp:open-paywall', onOpen);
    return () => window.removeEventListener('mjp:open-paywall', onOpen);
  }, []);

  if (!info) return null;
  return <PaywallModal info={info} onClose={() => setInfo(null)} />;
}

export default PaywallModal;
