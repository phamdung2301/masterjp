import { Link } from 'react-router-dom';

/**
 * EmptyState thân thiện — dùng khi tab/section chưa có dữ liệu.
 * Phong cách washi + Tanu-chan, thay cho các message khô khan.
 *
 * Props:
 * - badge: ký tự Nhật trong badge (vd "復", "辞") — dùng khi không có mascotImage
 * - mascotImage: đường dẫn ảnh mascot (mặc định /assets/footer-mascot.png)
 * - title: tiêu đề ngắn gọn, ấm áp
 * - message: mô tả thân thiện
 * - actionTo / actionLabel: nút CTA (Link) — vd { actionTo: '/hoc', actionLabel: 'Học từ mới' }
 * - onAction / actionLabel: nút CTA dạng button với handler
 */
export default function EmptyState({
  badge,
  mascotImage = '/assets/footer-mascot.png',
  title,
  message,
  actionTo,
  onAction,
  actionLabel,
}) {
  return (
    <div className="empty-state empty-state-friendly">
      <div className="empty-mascot">
        <img
          src={mascotImage}
          alt="Tanu-chan"
          onError={(e) => {
            // Fallback: ẩn ảnh, hiện badge ký tự Nhật
            e.currentTarget.style.display = 'none';
            const fb = e.currentTarget.parentElement?.querySelector('.empty-badge-fallback');
            if (fb) fb.style.display = 'inline-flex';
          }}
        />
        {badge && (
          <span className="jp-badge matcha empty-badge-fallback" style={{ display: 'none' }}>
            {badge}
          </span>
        )}
      </div>
      {title && <h3 className="empty-title">{title}</h3>}
      {message && <p>{message}</p>}
      {actionTo && actionLabel && (
        <Link to={actionTo} className="btn btn-primary">
          {actionLabel}
        </Link>
      )}
      {onAction && actionLabel && !actionTo && (
        <button type="button" onClick={onAction} className="btn btn-primary">
          {actionLabel}
        </button>
      )}
    </div>
  );
}
