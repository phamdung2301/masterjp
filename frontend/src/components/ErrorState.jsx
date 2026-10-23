/**
 * ErrorState — trạng thái "GẶP LỖI" (API fail, network...), phân biệt rõ với EmptyState
 * ("chưa có dữ liệu"). Cùng họ washi nhưng mood cảnh báo nhẹ:
 * - Nền kem pha hồng nhạt, viền đỏ shu-iro mờ
 * - Tanu-chan + headline "trục trặc"
 * - Chi tiết kỹ thuật hiện nhỏ (để báo lỗi có thông tin)
 * - Nút "Thử lại"
 *
 * Props:
 * - technical: message kỹ thuật từ backend (hiện nhỏ, muted)
 * - onRetry: handler cho nút "Thử lại"
 * - title/message: tùy chỉnh headline
 */
export default function ErrorState({
  technical,
  onRetry,
  title = 'Tanu-chan gặp chút trục trặc rồi!',
  message = 'Không tải được dữ liệu. Bạn thử lại giúp mình nhé — nếu vẫn lỗi, chụp màn hình này gửi cho mình.',
}) {
  return (
    <div className="page">
      <div className="empty-state empty-washi error-washi" role="alert">
        <div className="empty-washi-art">
          <div className="empty-sun error-sun">
            <img
              src="/assets/footer-mascot.png"
              alt="Tanu-chan"
              onError={(e) => {
                e.currentTarget.style.display = 'none';
              }}
            />
          </div>
        </div>
        <h3 className="empty-washi-title error-washi-title">{title}</h3>
        <p className="empty-washi-msg">{message}</p>
        {technical && <p className="error-technical">Chi tiết kỹ thuật: {technical}</p>}
        {onRetry && (
          <button type="button" onClick={onRetry} className="btn btn-primary empty-washi-cta">
            Thử lại
          </button>
        )}
      </div>
    </div>
  );
}
