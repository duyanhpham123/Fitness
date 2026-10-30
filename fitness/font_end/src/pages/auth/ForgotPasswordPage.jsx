import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../../api/client.js';
import { Field, useForm, useToast } from '../../components/ui.jsx';
import AuthShell from './AuthShell.jsx';

export default function ForgotPasswordPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const [sent, setSent] = useState(null);
  const [error, setError] = useState(null);
  const form = useForm({ email: '', otp: '', newPassword: '' });

  const sendCode = async (e) => {
    e.preventDefault();
    setError(null);
    try {
      const res = await form.submit((v) =>
        api('/api/auth/forgot-password', { method: 'POST', auth: false, body: { email: v.email } }));
      setSent(res);
      if (res.devOtp) form.set('otp', res.devOtp);
    } catch (err) {
      setError(err.message);
    }
  };

  const reset = async (e) => {
    e.preventDefault();
    setError(null);
    try {
      await form.submit((v) => api('/api/auth/reset-password', { method: 'POST', auth: false, body: v }));
      toast('Đổi mật khẩu thành công, hãy đăng nhập lại', 'success');
      navigate('/login');
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <AuthShell title="Quên mật khẩu">
      {error && <div className="alert error">{error}</div>}
      {!sent ? (
        <form onSubmit={sendCode}>
          <Field label="Email đăng ký" error={form.errors.email}><input {...form.bind('email', 'email')} required /></Field>
          <button className="btn block" disabled={form.submitting}>Gửi mã OTP</button>
        </form>
      ) : (
        <form onSubmit={reset}>
          <div className="alert info">{sent.message}</div>
          {sent.devOtp && <div className="alert success small">Môi trường dev: mã OTP là <b>{sent.devOtp}</b></div>}
          <Field label="Mã OTP" error={form.errors.otp}><input {...form.bind('otp')} maxLength={6} required /></Field>
          <Field label="Mật khẩu mới" error={form.errors.newPassword} hint="Ít nhất 8 ký tự, có cả chữ và số">
            <input {...form.bind('newPassword', 'password')} autoComplete="new-password" required />
          </Field>
          <button className="btn block" disabled={form.submitting}>Đặt lại mật khẩu</button>
        </form>
      )}
      <p className="muted" style={{ textAlign: 'center', marginTop: 16 }}><Link to="/login">Quay lại đăng nhập</Link></p>
    </AuthShell>
  );
}
