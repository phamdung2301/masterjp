import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';
import './pages.css';

// US-001: trang chủ giới thiệu Master JP cho guest — hero, tính năng, CTA đăng ký.
export default function Home() {
  const { isLoggedIn } = useAuth();
  return (
    <div>
      <section className="hero">
        <div className="hero-inner">
          <div className="hero-badge">Minna no Nihongo · N5–N4</div>
          <h1>Học từ vựng tiếng Nhật<br />đúng tiến trình giáo trình</h1>
          <p className="hero-sub">
            Master JP giúp bạn học từ vựng theo từng bài Minna no Nihongo: flashcard,
            quiz 3 dạng (Nhật → Việt, Việt → Nhật, Nghe), bộ thẻ tự chọn và ôn tập
            ngắt quãng nhắc đúng lúc — không bao giờ quên từ đã học.
          </p>
          <div className="hero-actions">
            {isLoggedIn ? (
              <>
                <Link to="/bai-hoc" className="btn btn-primary btn-lg">Tiếp tục học</Link>
                <Link to="/on-tap" className="btn btn-outline btn-lg">Ôn tập đến hạn</Link>
              </>
            ) : (
              <>
                <Link to="/dang-ky" className="btn btn-primary btn-lg">Đăng ký miễn phí</Link>
                <Link to="/bai-hoc" className="btn btn-outline btn-lg">Xem thử bài học</Link>
              </>
            )}
          </div>
        </div>
      </section>

      <section className="features">
        <div className="page">
          <h2>Tại sao học với Master JP?</h2>
          <div className="feature-grid">
            <div className="feature-card">
              <div className="feature-icon">📚</div>
              <h3>Đúng giáo trình Minna no Nihongo</h3>
              <p>50 bài N5–N4, mỗi bài chia rõ Từ vựng chính, Từ tham khảo và Cụm từ. Audio phát âm chuẩn cho từng từ.</p>
            </div>
            <div className="feature-card">
              <div className="feature-icon">🃏</div>
              <h3>Flashcard lật thẻ</h3>
              <p>Tự đánh giá Nhớ / Chưa nhớ. Mỗi lần đánh giá được lưu ngay — thoát giữa chừng cũng không mất.</p>
            </div>
            <div className="feature-card">
              <div className="feature-icon">✏️</div>
              <h3>Quiz 3 dạng</h3>
              <p>Nhật → Việt, Việt → Nhật và Nghe → chọn. Chấm ngay từng câu, cuối phiên có danh sách câu sai để ôn.</p>
            </div>
            <div className="feature-card">
              <div className="feature-icon">🧠</div>
              <h3>Ôn ngắt quãng (SM-2)</h3>
              <p>Hệ thống nhớ từng từ của bạn và nhắc ôn đúng lúc: từ càng khó càng được ôn nhiều, từ đã thuộc giãn dần.</p>
            </div>
            <div className="feature-card">
              <div className="feature-icon">🏷️</div>
              <h3>Bộ thẻ tự chọn</h3>
              <p>Gom từ khó từ nhiều bài vào bộ thẻ riêng. Thành viên Premium có thể chia sẻ công khai cho cộng đồng.</p>
            </div>
            <div className="feature-card">
              <div className="feature-icon">📈</div>
              <h3>Theo dõi tiến trình</h3>
              <p>Streak ngày học liên tiếp, số từ đã thuộc theo từng bài và cấp độ, lịch sử các phiên học.</p>
            </div>
          </div>
        </div>
      </section>

      {!isLoggedIn && (
        <section className="cta">
          <div className="page cta-inner">
            <h2>Bắt đầu học miễn phí ngay hôm nay</h2>
            <p>Đăng ký chỉ cần email — hệ thống gửi mã xác thực trong vài giây.</p>
            <Link to="/dang-ky" className="btn btn-primary btn-lg">Tạo tài khoản miễn phí</Link>
          </div>
        </section>
      )}
    </div>
  );
}
