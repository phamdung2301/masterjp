import { Link } from 'react-router-dom';

/**
 * EmptyState — trạng thái "chưa có dữ liệu", trang trí theo concept washi v2.0:
 * - Nền giấy washi + họa tiết sóng seigaiha mờ
 * - Tanu-chan trong vòng tròn "mặt trời mọc" (viền đỏ shu-iro)
 * - Badge kanji dạng con dấu hanko đỏ
 * - Tiêu đề chàm ai-iro, nút CTA đỏ shu-iro
 *
 * DÙNG KHI: danh sách trống, chưa có dữ liệu (KHÔNG phải khi lỗi API —
 * lỗi dùng <ErrorState /> để user phân biệt được 2 trường hợp).
 *
 * Props:
 * - badge: ký tự kanji cho con dấu (vd "復", "辞") — fallback khi mascot lỗi
 * - mascotImage: đường dẫn ảnh Tanu-chan (mặc định /assets/footer-mascot.png)
 * - title: tiêu đề ngắn, ấm áp
 * - message: mô tả thân thiện
 * - actionTo + actionLabel: CTA dạng Link | onAction + actionLabel: CTA dạng button
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
    <div className="empty-state empty-washi" role="status">
      <div className="empty-washi-art">
        <div className="empty-sun">
          <img
            src={mascotImage}
            alt="Tanu-chan"
            onError={(e) => {
              e.currentTarget.style.display = 'none';
              const fb = e.currentTarget.parentElement?.querySelector('.empty-hanko');
              if (fb) fb.classList.add('show');
            }}
          />
          {badge && <span className="empty-hanko">{badge}</span>}
        </div>
      </div>
      {title && <h3 className="empty-washi-title">{title}</h3>}
      {message && <p className="empty-washi-msg">{message}</p>}
      {actionTo && actionLabel && (
        <Link to={actionTo} className="btn btn-primary empty-washi-cta">
          {actionLabel}
        </Link>
      )}
      {onAction && actionLabel && !actionTo && (
        <button type="button" onClick={onAction} className="btn btn-primary empty-washi-cta">
          {actionLabel}
        </button>
      )}
    </div>
  );
}
