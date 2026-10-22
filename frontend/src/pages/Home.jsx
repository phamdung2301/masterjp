import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';
import './pages.css';

// v2.0 (A4): trang chủ guest theo pv-trang-chu.html — hero, 6 card tính năng,
// bảng giá 3 gói (chỉ hiển thị), footer dùng chung.
// Ảnh /assets/* do user upload tay sau merge; mỗi <img> nằm trong .safe-img
// (nền màu + chữ 漢 mờ) nên layout vẫn đẹp khi file 404.
function SafeImg({ src, alt, className }) {
  const [ok, setOk] = useState(true);
  return (
    <div className={`safe-img${className ? ` ${className}` : ''}`}>
      {ok && <img src={src} alt={alt} onError={() => setOk(false)} loading="lazy" />}
    </div>
  );
}

const FEATURES = [
  {
    img: '/assets/card-vocab.jpeg', badge: '語', title: 'Từ vựng theo bài',
    desc: '50 bài Minna no Nihongo, mỗi từ có audio phát âm chuẩn, ví dụ và cụm chủ đề thông minh.',
    link: '/hoc', linkText: 'Khám phá',
  },
  {
    img: '/assets/card-flashcard.jpeg', badge: '札', title: 'Flashcard 3D',
    desc: 'Thẻ lật 3D mượt mà. Tự đánh giá Nhớ / Chưa nhớ — thoát giữa chừng cũng không mất tiến trình.',
    link: '/hoc', linkText: 'Thử ngay',
  },
  {
    img: '/assets/card-quiz.jpeg', badge: '問', title: 'Quiz 4 dạng',
    desc: 'Nhật → Việt, Việt → Nhật, Luyện nghe, Gõ từ. Chấm ngay từng câu, tổng hợp câu sai để ôn lại.',
    link: '/hoc', linkText: 'Làm thử',
  },
  {
    img: '/assets/card-grammar.jpeg', badge: '文', title: 'Ngữ pháp 145 điểm',
    desc: 'Công thức + ví dụ + bài tập cho từng điểm ngữ pháp N5–N4, tra cứu theo bài học.',
    link: '/ngu-phap', linkText: 'Xem ngữ pháp',
  },
  {
    img: '/assets/card-kanji.jpeg', badge: '漢', title: 'Kanji 988 chữ',
    desc: 'Học kanji theo từng bài, xem âm đọc và nghĩa từng chữ, flashcard và quiz kanji riêng.',
    link: '/kanji', linkText: 'Học kanji',
  },
  {
    img: '/assets/card-review.jpeg', badge: '復', title: 'Ôn tập SM-2',
    desc: 'Hệ thống nhớ từng từ của bạn và nhắc ôn đúng lúc — từ khó ôn nhiều, từ thuộc giãn dần.',
    link: '/on-tap', linkText: 'Ôn ngay',
  },
];

const PLANS = [
  {
    name: 'Free', price: '0đ', per: 'mãi mãi',
    features: ['20 từ vựng / ngày', 'Flashcard & Quiz cơ bản', 'Ôn tập SM-2'],
    cta: 'Bắt đầu', hot: false,
  },
  {
    name: 'Premium tháng', price: '49k', per: '/ tháng', tag: 'PHỔ BIẾN NHẤT',
    features: ['Không giới hạn từ vựng', 'Luyện nghe + Gõ từ', 'Chia sẻ bộ thẻ công khai', 'Thống kê chi tiết'],
    cta: 'Nâng cấp ngay', hot: true,
  },
  {
    name: 'Premium năm', price: '390k', per: '/ năm · tiết kiệm 34%',
    features: ['Mọi quyền lợi Premium tháng', 'Huy hiệu Người ủng hộ', 'Ưu tiên tính năng mới'],
    cta: 'Chọn gói năm', hot: false,
  },
];

export default function Home() {
  const { isLoggedIn } = useAuth();

  return (
    <div>
      {/* Hero */}
      <section className="home2-hero">
        <div className="home2-hero-text">
          <span className="home2-badge">MINNA NO NIHONGO · N5–N4 · 50 BÀI</span>
          <h1 className="home2-title">
            Học tiếng Nhật <span className="jp-accent">vui như chơi,</span><br />
            nhớ lâu như in
          </h1>
          <p className="home2-sub">
            Cùng Tanu-chan chinh phục 2.692 từ vựng, 145 điểm ngữ pháp và 988 kanji
            theo đúng tiến trình giáo trình — với flashcard 3D, quiz thông minh
            và ôn tập ngắt quãng SM-2.
          </p>
          <div className="home2-actions">
            {isLoggedIn ? (
              <>
                <Link to="/hoc" className="btn btn-primary btn-lg">Tiếp tục học</Link>
                <Link to="/on-tap" className="btn btn-outline btn-lg">Ôn tập đến hạn</Link>
              </>
            ) : (
              <>
                <Link to="/dang-ky" className="btn btn-primary btn-lg">Bắt đầu miễn phí</Link>
                <Link to="/hoc" className="btn btn-outline btn-lg">Xem thử bài 1</Link>
              </>
            )}
          </div>
          <div className="home2-stats">
            <div className="home2-stat"><b>2.692</b><span>từ vựng có audio</span></div>
            <div className="home2-stat"><b>145</b><span>điểm ngữ pháp</span></div>
            <div className="home2-stat"><b>988</b><span>kanji</span></div>
          </div>
        </div>
        <div className="home2-hero-media">
          <SafeImg src="/assets/mascot-hero.png" alt="Tanu-chan — linh vật Master JP" />
        </div>
      </section>

      {/* 6 card tính năng */}
      <section className="home2-section">
        <h2>Tại sao học với Master JP?</h2>
        <p className="sub">Mọi thứ bạn cần để từ N5 lên N4 — trong một nơi duy nhất</p>
        <div className="home2-cards">
          {FEATURES.map((f) => (
            <article key={f.title} className="home2-card">
              <SafeImg src={f.img} alt={f.title} />
              <div className="home2-card-body">
                <h3><span className="jp-badge">{f.badge}</span> {f.title}</h3>
                <p>{f.desc}</p>
                <Link to={f.link} className="home2-card-link">{f.linkText} →</Link>
              </div>
            </article>
          ))}
        </div>
      </section>

      {/* Bảng giá — chỉ hiển thị (Phase D mới gắn thanh toán) */}
      <section className="home2-section" id="bang-gia">
        <h2>Bảng giá đơn giản</h2>
        <p className="sub">Bắt đầu miễn phí — nâng cấp khi bạn sẵn sàng bứt phá</p>
        <div className="home2-pricing">
          {PLANS.map((p) => (
            <div key={p.name} className={`home2-price-card${p.hot ? ' hot' : ''}`}>
              {p.tag && <span className="tag">{p.tag}</span>}
              <h3>{p.name}</h3>
              <div className="home2-price">{p.price}</div>
              <div className="per">{p.per}</div>
              <ul>
                {p.features.map((ft) => <li key={ft}>{ft}</li>)}
              </ul>
              <Link to="/dang-ky" className={`btn ${p.hot ? 'btn-primary' : 'btn-outline'}`}>{p.cta}</Link>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
