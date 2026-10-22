import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';
import { getErrorMessage } from '../api/client.js';
import './pages.css';

// US-002 (F-003): đăng nhập email + mật khẩu.
export default function Login() {
  const navigate = useNavigate();
  const location = useLocation();
  const { login } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const infoMessage = location.state?.message;

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await login(email.trim(), password);
      navigate('/ca-nhan', { replace: true });
    } catch (err) {
      setError(getErrorMessage(err, 'Đăng nhập thất bại. Kiểm tra lại email và mật khẩu.'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="page auth-page">
      <div className="auth-card">
        <h1>Đăng nhập</h1>
        <p className="muted">Chào mừng bạn quay lại Master JP!</p>
        {error && <div className="alert alert-error">{error}</div>}
        {infoMessage && <div className="alert alert-success">{infoMessage}</div>}
        <form onSubmit={handleSubmit}>
          <label>
            Email
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoFocus placeholder="ban@email.com" />
          </label>
          <label>
            Mật khẩu
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
          </label>
          <button type="submit" className="btn btn-primary btn-lg btn-block" disabled={loading}>
            {loading ? 'Đang đăng nhập…' : 'Đăng nhập'}
          </button>
        </form>
        <div className="auth-links">
          <Link to="/quen-mat-khau">Quên mật khẩu?</Link>
          <span>·</span>
          <Link to="/dang-ky">Chưa có tài khoản? Đăng ký</Link>
        </div>
      </div>
    </div>
  );
}
