import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { requestOtp, resetPassword } from '../api/auth.js';
import { getErrorMessage } from '../api/client.js';
import './pages.css';

// US-002 (F-004): quên mật khẩu — (1) email → gửi mã, (2) mã + mật khẩu mới.
export default function ForgotPassword() {
  const navigate = useNavigate();
  const [step, setStep] = useState(1);
  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSendOtp = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await requestOtp(email.trim(), 'RESET_PASSWORD');
      setStep(2);
    } catch (err) {
      setError(getErrorMessage(err, 'Không gửi được mã. Kiểm tra lại email.'));
    } finally {
      setLoading(false);
    }
  };

  const handleReset = async (e) => {
    e.preventDefault();
    setError('');
    if (newPassword.length < 8) { setError('Mật khẩu mới phải có ít nhất 8 ký tự.'); return; }
    setLoading(true);
    try {
      await resetPassword({ email: email.trim(), code: code.trim(), newPassword });
      navigate('/dang-nhap', { replace: true, state: { message: 'Đã đặt lại mật khẩu. Đăng nhập bằng mật khẩu mới nhé!' } });
    } catch (err) {
      setError(getErrorMessage(err, 'Mã xác thực không đúng hoặc đã hết hạn.'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="page auth-page">
      <div className="auth-card">
        <h1>Quên mật khẩu</h1>
        {error && <div className="alert alert-error">{error}</div>}
        {step === 1 ? (
          <form onSubmit={handleSendOtp}>
            <p className="muted">Nhập email đăng ký — chúng tôi sẽ gửi mã xác thực để đặt lại mật khẩu.</p>
            <label>
              Email
              <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoFocus placeholder="ban@email.com" />
            </label>
            <button type="submit" className="btn btn-primary btn-lg btn-block" disabled={loading}>
              {loading ? 'Đang gửi…' : 'Gửi mã xác thực'}
            </button>
          </form>
        ) : (
          <form onSubmit={handleReset}>
            <p className="muted">Mã xác thực đã được gửi tới <strong>{email}</strong> (hiệu lực 5 phút).</p>
            <label>
              Mã xác thực
              <input type="text" value={code} onChange={(e) => setCode(e.target.value)} required autoFocus
                inputMode="numeric" maxLength={6} placeholder="123456" />
            </label>
            <label>
              Mật khẩu mới (ít nhất 8 ký tự)
              <input type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} required minLength={8} />
            </label>
            <button type="submit" className="btn btn-primary btn-lg btn-block" disabled={loading}>
              {loading ? 'Đang lưu…' : 'Đặt lại mật khẩu'}
            </button>
          </form>
        )}
        <div className="auth-links">
          <Link to="/dang-nhap">Quay lại đăng nhập</Link>
        </div>
      </div>
    </div>
  );
}
