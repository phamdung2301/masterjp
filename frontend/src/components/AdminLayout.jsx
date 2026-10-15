import { NavLink, Outlet } from 'react-router-dom';
import '../pages/admin/Admin.css';

// Layout riêng cho admin: sidebar + nội dung. Chỉ ROLE_ADMIN (AdminRoute guard).
export default function AdminLayout() {
  return (
    <div className="page admin-page">
      <h1>Quản trị</h1>
      <div className="admin-layout">
        <aside className="admin-sidebar">
          <NavLink to="/admin" end>Tổng quan</NavLink>
          <NavLink to="/admin/bai-hoc">Bài học</NavLink>
          <NavLink to="/admin/tu-vung">Từ vựng</NavLink>
          <NavLink to="/admin/import">Import Excel</NavLink>
          <NavLink to="/admin/nguoi-dung">Người dùng</NavLink>
          <NavLink to="/admin/nhat-ky">Nhật ký</NavLink>
        </aside>
        <div className="admin-content">
          <Outlet />
        </div>
      </div>
    </div>
  );
}
