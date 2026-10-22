import { useState } from 'react';
import { Link } from 'react-router-dom';

// v2.0 (A4): footer washi — nền chàm, mascot, link điều hướng.
// Ảnh /assets/footer-mascot.png do user upload sau merge; khi chưa có,
// container giữ nền màu (class footer-mascot) nên layout vẫn đẹp.
export default function Footer() {
  const [imgOk, setImgOk] = useState(true);
  return (
    <footer className="site-footer">
      <div className="footer-inner">
        <div className="footer-brand">
          {imgOk ? (
            <img
              src="/assets/footer-mascot.png"
              alt="Tanu-chan — linh vật Master JP"
              className="footer-mascot"
              onError={() => setImgOk(false)}
            />
          ) : (
            <div className="footer-mascot" aria-hidden="true" />
          )}
          <div>
            <strong>Master JP</strong>
            <p className="footer-note" style={{ margin: '6px 0 0' }}>
              Đồng hành cùng bạn từ N5 đến N4 — và xa hơn nữa.
              Minna no Nihongo · Flashcard · Quiz · Kanji · Ngữ pháp.
            </p>
          </div>
        </div>
        <nav className="footer-links" aria-label="Liên kết nhanh">
          <Link to="/hoc">Bài học</Link>
          <Link to="/kanji">Kanji</Link>
          <Link to="/ngu-phap">Ngữ pháp</Link>
          <Link to="/tu-dien">Từ điển</Link>
          <Link to="/ca-nhan">Cá nhân</Link>
        </nav>
      </div>
      <div className="footer-copy">© 2026 Master JP · Học tiếng Nhật vui như chơi, nhớ lâu như in</div>
    </footer>
  );
}
