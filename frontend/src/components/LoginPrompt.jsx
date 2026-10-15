import { Link } from 'react-router-dom';

// Modal mời đăng nhập: hiện khi GUEST bấm "Luyện tập" / "Thêm vào bộ thẻ".
export default function LoginPrompt({ open, onClose, message }) {
  if (!open) return null;
  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h3>Đăng nhập để tiếp tục</h3>
        <p className="modal-text">{message || 'Tính năng này cần tài khoản. Đăng nhập để lưu tiến trình và luyện tập.'}</p>
        <div className="modal-actions">
          <Link to="/dang-nhap" className="btn btn-primary" onClick={onClose}>Đăng nhập</Link>
          <Link to="/dang-ky" className="btn btn-outline" onClick={onClose}>Đăng ký miễn phí</Link>
          <button type="button" className="btn btn-ghost" onClick={onClose}>Để sau</button>
        </div>
      </div>
    </div>
  );
}
