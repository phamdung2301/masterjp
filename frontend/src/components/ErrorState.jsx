/**
 * ErrorState thân thiện — dùng khi gọi API thất bại.
 * Thay cho việc hiện raw message backend ("Không tìm thấy tài nguyên.") một cách khô khan:
 * - Headline ấm áp với Tanu-chan
 * - Chi tiết kỹ thuật hiện nhỏ bên dưới (để user báo lỗi có thông tin)
 * - Nút thử lại
 *
 * Props:
 * - technical: message kỹ thuật từ backend (hiện nhỏ, muted)
 * - onRetry: handler cho nút "Thử lại"
 * - title/message: tùy chỉnh headline (mặc định thân thiện)
 */
export default function ErrorState({
  technical,
  onRetry,
  title = 'Tanu-chan gặp chút trục trặc rồi!',
  message = 'Không tải được dữ liệu. Bạn thử lại giúp mình nhé — nếu vẫn lỗi, chụp màn hình này gửi cho mình.',
}) {
  return (
    <div className="page">
      <div className="empty-state empty-state-friendly error-state">
        <div className="empty-mascot">
          <img
            src="/assets/footer-mascot.png"
            alt="Tanu-chan"
            onError={(e) => {
              e.currentTarget.style.display = 'none';
            }}
          />
        </div>
        <h3 className="empty-title">{title}</h3>
        <p>{message}</p>
        {technical && <p className="error-technical">Chi tiết: {technical}</p>}
        {onRetry && (
          <button type="button" onClick={onRetry} className="btn btn-primary">
            Thử lại
          </button>
        )}
      </div>
    </div>
  );
}
