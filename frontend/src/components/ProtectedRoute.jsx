import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';

// Chỉ user đã đăng nhập mới vào. Guest → /dang-nhap.
export function ProtectedRoute({ children }) {
  const { user, loading, isLoggedIn } = useAuth();
  if (loading) return <div className="page"><p className="muted">Đang tải…</p></div>;
  if (!isLoggedIn) return <Navigate to="/dang-nhap" replace />;
  return children;
}

// Chỉ ROLE_ADMIN mới vào /admin.
export function AdminRoute({ children }) {
  const { user, loading, isLoggedIn } = useAuth();
  if (loading) return <div className="page"><p className="muted">Đang tải…</p></div>;
  if (!isLoggedIn || user?.role !== 'ADMIN') return <Navigate to="/" replace />;
  return children;
}
