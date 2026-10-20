import { Component } from 'react';

/**
 * ErrorBoundary — bọc <Routes>: bất kỳ lỗi render nào ở 1 trang
 * cũng hiện màn hình lỗi thân thiện (có nút thử lại / về trang chủ)
 * thay vì trắng tinh toàn app (bài học xương máu: React unmount toàn bộ cây
 * khi throw mà không có boundary).
 *
 * Rule: dùng cho mọi React app generate ra từ nay về sau.
 */
export default class ErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }

  componentDidCatch(error, info) {
    // Giữ log ở console để debug; không gửi đi đâu (không có error tracking service).
    console.error('[ErrorBoundary]', error, info?.componentStack);
  }

  handleRetry = () => {
    this.setState({ hasError: false, error: null });
  };

  handleHome = () => {
    window.location.href = '/';
  };

  render() {
    if (this.state.hasError) {
      return (
        <div className="page error-boundary-page">
          <h1>Đã có lỗi xảy ra</h1>
          <p className="muted">
            Trang này gặp sự cố khi hiển thị. Bạn có thể thử tải lại
            hoặc quay về trang chủ — dữ liệu học của bạn vẫn an toàn.
          </p>
          {this.state.error?.message && (
            <details className="error-details">
              <summary>Chi tiết kỹ thuật</summary>
              <pre>{String(this.state.error.message)}</pre>
            </details>
          )}
          <div className="result-actions">
            <button type="button" className="btn btn-primary" onClick={this.handleRetry}>
              Thử lại
            </button>
            <button type="button" className="btn btn-outline" onClick={this.handleHome}>
              Về trang chủ
            </button>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}
