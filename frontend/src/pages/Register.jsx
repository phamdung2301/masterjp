import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';
import { requestOtp } from '../api/auth.js';
import { getErrorMessage } from '../api/client.js';
import './pages.css';

// US-001 (F-001, F-002): đăng ký 2 bước — (1) email → gửi mã OTP, (2) nhập mã + đặt mật khẩu + tên.
export default function Register() {
  const navigate = useNavigate();
  const { register } = useAuth();
  const [step, setStep] = useState(1);
  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [password, setPassword] = useState('');
  const [name, setName] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [cooldown, setCooldown] = useState(0);

  const startCooldown = () => {
    setCooldown(60);
    const t = setInterval(() => {
      setCooldown((c) => {
        if (c <= 1) { clearInterval(t); return 0; }
        return c - 1;
      });
    }, 1000);
  };

  const handleSendOtp = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await requestOtp(email.trim(), 'REGISTER');
      setStep(2);
      startCooldown();
    } catch (err) {
      setError(getErrorMessage(err, 'Không gửi được mã. Kiểm tra lại email.'));
    } finally {
      setLoading(false);
    }
  };

  const handleRegister = async (e) => {
    e.preventDefault();
    setError('');
    if (password.length < 8) { setError('Mật khẩu phải có ít nhất 8 ký tự.'); return; }
    if (!name.trim()) { setError('Vui lòng nhập tên hiển thị.'); return; }
    setLoading(true);
    try {
      await register({ email: email.trim(), code: code.trim(), password, name: name.trim() });
      navigate('/tong-quan', { replace: true });
    } catch (err) {
      setError(getErrorMessage(err, 'Mã xác thực không đúng hoặc đã hết hạn.'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="page auth-page">
      <div className="auth-card">
        <h1>Đăng ký</h1>
        <p className="muted">Miễn phí — chỉ cần email để bắt đầu.</p>
        {error && <div className="alert alert-error">{error}</div>}

        {step === 1 ? (
          <form onSubmit={handleSendOtp}>
            <label>
              Email
              <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoFocus placeholder="ban@email.com" />
            </label>
            <button type="submit" className="btn btn-primary btn-lg btn-block" disabled={loading}>
              {loading ? 'Đang gửi…' : 'Gửi mã xác thực'}
            </button>
          </form>
        ) : (
          <form onSubmit={handleRegister}>
            <p className="muted">Mã xác thực 6 số đã được gửi tới <strong>{email}</strong> (hiệu lực 5 phút).</p>
            <label>
              Mã xác thực
              <input type="text" value={code} onChange={(e) => setCode(e.target.value)} required autoFocus
                inputMode="numeric" maxLength={6} placeholder="123456" />
            </label>
            <label>
              Tên hiển thị
              <input type="text" value={name} onChange={(e) => setName(e.target.value)} required maxLength={50} placeholder="Tên của bạn" />
            </label>
            <label>
              Mật khẩu (ít nhất 8 ký tự)
              <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required minLength={8} />
            </label>
            <button type="submit" className="btn btn-primary btn-lg btn-block" disabled={loading}>
              {loading ? 'Đang tạo tài khoản…' : 'Hoàn tất đăng ký'}
            </button>
            <button type="button" className="btn btn-ghost btn-block" disabled={loading || cooldown > 0} onClick={handleSendOtp}>
              {cooldown > 0 ? `Gửi lại mã sau ${cooldown}s` : 'Gửi lại mã'}
            </button>
          </form>
        )}
        <div className="auth-links">
          <Link to="/dang-nhap">Đã có tài khoản? Đăng nhập</Link>
        </div>
      </div>
    </div>
  );
}
