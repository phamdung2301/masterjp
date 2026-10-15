import { Link, NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';

export default function Header() {
  const { user, isLoggedIn, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/');
  };

  return (
    <header className="site-header">
      <div className="header-inner">
        <Link to="/" className="brand">
          <span className="brand-icon">🎌</span>
          <span className="brand-name">Master JP</span>
        </Link>
        <nav className="main-nav">
          <NavLink to="/bai-hoc">Bài học</NavLink>
          <NavLink to="/kanji">Kanji</NavLink>
          <NavLink to="/tu-dien">Từ điển</NavLink>
          <NavLink to="/luyen-tap-nhanh">Luyện nhanh</NavLink>
          <NavLink to="/on-tap">Ôn tập</NavLink>
          <NavLink to="/bo-the">Bộ thẻ</NavLink>
          <NavLink to="/kham-pha">Khám phá</NavLink>
          <NavLink to="/tong-quan">Tổng quan</NavLink>
          {user?.role === 'ADMIN' && <NavLink to="/admin" className="admin-link">Quản trị</NavLink>}
        </nav>
        <div className="header-auth">
          {isLoggedIn ? (
            <>
              <span className="header-user" title={user.email}>
                {user.name || user.email}
                {user.streakDays > 0 && <span className="streak-badge">🔥 {user.streakDays}</span>}
              </span>
              <button type="button" className="btn btn-sm btn-outline" onClick={handleLogout}>Đăng xuất</button>
            </>
          ) : (
            <>
              <Link to="/dang-nhap" className="btn btn-sm btn-outline">Đăng nhập</Link>
              <Link to="/dang-ky" className="btn btn-sm btn-primary">Đăng ký</Link>
            </>
          )}
        </div>
      </div>
    </header>
  );
}
