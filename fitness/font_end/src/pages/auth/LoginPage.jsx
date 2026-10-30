import { useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext.jsx';
import { Field, useForm } from '../../components/ui.jsx';
import AuthShell from './AuthShell.jsx';

export default function LoginPage() {
  const { user, login, homePath } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const form = useForm({ email: '', password: '', rememberMe: false });
  const [error, setError] = useState(null);

  if (user) return <Navigate to={homePath()} replace />;

  const onSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    try {
      await form.submit((v) => login(v.email, v.password, v.rememberMe));
      navigate(location.state?.from || '/', { replace: true });
    } catch (err) {
      // Tài khoản chưa xác thực email -> chuyển sang bước nhập OTP
      if (err.code === 'ACCOUNT_NOT_VERIFIED') {
        navigate('/register', { state: { verifyEmail: form.values.email } });
        return;
      }
      setError(err.message);
    }
  };

  return (
    <AuthShell title="Đăng nhập">
      {error && <div className="alert error">{error}</div>}
      <form onSubmit={onSubmit}>
        <Field label="Email" error={form.errors.email}>
          <input {...form.bind('email', 'email')} placeholder="ban@email.com" autoComplete="username" required />
        </Field>
        <Field label="Mật khẩu" error={form.errors.password}>
          <input {...form.bind('password', 'password')} autoComplete="current-password" required />
        </Field>
        <div className="row" style={{ marginBottom: 16 }}>
          <label className="checkbox">
            <input {...form.bind('rememberMe', 'checkbox')} className="" /> Ghi nhớ đăng nhập
          </label>
          <span className="spacer" />
          <Link to="/forgot-password">Quên mật khẩu?</Link>
        </div>
        <button className="btn block" disabled={form.submitting}>
          {form.submitting ? 'Đang đăng nhập...' : 'Đăng nhập'}
        </button>
      </form>
      <p className="muted" style={{ textAlign: 'center', marginTop: 16 }}>
        Chưa có tài khoản? <Link to="/register">Đăng ký ngay</Link>
      </p>
    </AuthShell>
  );
}
