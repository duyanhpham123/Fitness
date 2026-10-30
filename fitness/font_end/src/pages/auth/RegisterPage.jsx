import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { api } from '../../api/client.js';
import { useAuth } from '../../auth/AuthContext.jsx';
import { Field, useForm, useToast } from '../../components/ui.jsx';
import AuthShell from './AuthShell.jsx';

/**
 * 2 bước: (1) điền thông tin -> backend tạo tài khoản PENDING và gửi OTP,
 * (2) nhập OTP -> kích hoạt và đăng nhập luôn.
 */
export default function RegisterPage() {
  const location = useLocation();
  const [email, setEmail] = useState(location.state?.verifyEmail || null);
  const [devOtp, setDevOtp] = useState(null);

  return (
    <AuthShell title={email ? 'Xác thực email' : 'Đăng ký hội viên'}>
      {email
        ? <VerifyStep email={email} devOtp={devOtp} />
        : <InfoStep onDone={(e, otp) => { setEmail(e); setDevOtp(otp); }} />}
      <p className="muted" style={{ textAlign: 'center', marginTop: 16 }}>
        Đã có tài khoản? <Link to="/login">Đăng nhập</Link>
      </p>
    </AuthShell>
  );
}

function InfoStep({ onDone }) {
  const form = useForm({ fullName: '', email: '', phone: '', password: '' });
  const [error, setError] = useState(null);

  const onSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    try {
      const res = await form.submit((v) =>
        api('/api/auth/register', { method: 'POST', auth: false, body: { ...v, phone: v.phone || null } }));
      onDone(res.user.email, res.otp?.devOtp);
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <form onSubmit={onSubmit}>
      {error && <div className="alert error">{error}</div>}
      <Field label="Họ tên" error={form.errors.fullName}><input {...form.bind('fullName')} required /></Field>
      <Field label="Email" error={form.errors.email}><input {...form.bind('email', 'email')} required /></Field>
      <Field label="Số điện thoại" error={form.errors.phone}><input {...form.bind('phone', 'tel')} placeholder="0912345678" /></Field>
      <Field label="Mật khẩu" error={form.errors.password} hint="Ít nhất 8 ký tự, có cả chữ và số">
        <input {...form.bind('password', 'password')} autoComplete="new-password" required />
      </Field>
      <button className="btn block" disabled={form.submitting}>Đăng ký</button>
    </form>
  );
}

function VerifyStep({ email, devOtp }) {
  const { verifyEmail } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const form = useForm({ otp: devOtp || '' });
  const [error, setError] = useState(null);
  const [hint, setHint] = useState(devOtp);

  const onSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    try {
      await form.submit((v) => verifyEmail(email, v.otp));
      toast('Kích hoạt tài khoản thành công', 'success');
      navigate('/member', { replace: true });
    } catch (err) {
      setError(err.message);
    }
  };

  const resend = async () => {
    try {
      const res = await api('/api/otp/send', { method: 'POST', auth: false, body: { email, purpose: 'VERIFY_EMAIL' } });
      setHint(res.devOtp);
      toast('Đã gửi lại mã OTP', 'success');
    } catch (err) {
      toast(err);
    }
  };

  return (
    <form onSubmit={onSubmit}>
      <div className="alert info">Mã OTP 6 số đã được gửi tới <b>{email}</b>, có hiệu lực 5 phút.</div>
      {hint && <div className="alert success small">Môi trường dev: mã OTP là <b>{hint}</b></div>}
      {error && <div className="alert error">{error}</div>}
      <Field label="Mã OTP" error={form.errors.otp}>
        <input {...form.bind('otp')} inputMode="numeric" maxLength={6} required />
      </Field>
      <button className="btn block" disabled={form.submitting}>Xác thực</button>
      <button type="button" className="btn ghost block" onClick={resend} style={{ marginTop: 8 }}>Gửi lại mã</button>
    </form>
  );
}
