import { Link, NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';
import { FlameIcon } from './icons.jsx';

// v2.0 (A3): 4 tab điều hướng — Học / Ôn tập / Từ điển / Cá nhân.
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
          <span className="jp-badge" aria-hidden="true">漢</span>
          <span className="brand-name">Master <span className="jp-accent">JP</span></span>
        </Link>
        <nav className="main-nav">
          <NavLink to="/hoc">Học</NavLink>
          <NavLink to="/on-tap">Ôn tập</NavLink>
          <NavLink to="/tu-dien">Từ điển</NavLink>
          <NavLink to="/ca-nhan">Cá nhân</NavLink>
          {user?.role === 'ADMIN' && <NavLink to="/admin" className="admin-link">Quản trị</NavLink>}
        </nav>
        <div className="header-auth">
          {isLoggedIn ? (
            <>
              <span className="header-user" title={user.email}>
                {user.name || user.email}
                {user.streakDays > 0 && (
                  <span className="streak-badge" title={`${user.streakDays} ngày học liên tiếp`}>
                    <FlameIcon size={14} /> {user.streakDays}
                  </span>
                )}
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
