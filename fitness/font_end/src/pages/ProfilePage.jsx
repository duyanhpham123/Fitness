import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client.js';
import { useAuth } from '../auth/AuthContext.jsx';
import { Field, PageHead, useForm, useToast } from '../components/ui.jsx';
import { label } from '../utils/format.js';

export default function ProfilePage() {
  const { user, hasRole } = useAuth();
  return (
    <>
      <PageHead title="Hồ sơ cá nhân" subtitle={`${user.email} · ${user.roles.map(label).join(', ')}`} />
      <div className="grid grid-2">
        <ProfileForm />
        <div className="stack">
          <PasswordForm />
          {hasRole('TRAINER') && <TrainerProfileForm trainerId={user.id} />}
        </div>
      </div>
    </>
  );
}

function ProfileForm() {
  const { user, reloadUser } = useAuth();
  const toast = useToast();
  const form = useForm({
    fullName: user.fullName, phone: user.phone || '', gender: user.gender || '',
    dateOfBirth: user.dateOfBirth || '', address: user.address || '', avatarUrl: user.avatarUrl || '',
  });

  const onSubmit = async (e) => {
    e.preventDefault();
    try {
      await form.submit((v) => api('/api/users/me', {
        method: 'PUT',
        body: { ...v, gender: v.gender || null, dateOfBirth: v.dateOfBirth || null, phone: v.phone || null },
      }));
      await reloadUser();
      toast('Đã lưu hồ sơ', 'success');
    } catch (err) {
      toast(err);
    }
  };

  return (
    <form className="card" onSubmit={onSubmit}>
      <h2 style={{ marginBottom: 16 }}>Thông tin cá nhân</h2>
      <Field label="Họ tên" error={form.errors.fullName}><input {...form.bind('fullName')} required /></Field>
      <div className="grid grid-2">
        <Field label="Số điện thoại" error={form.errors.phone}><input {...form.bind('phone', 'tel')} /></Field>
        <Field label="Giới tính">
          <select {...form.bind('gender')}>
            <option value="">-- Chọn --</option>
            <option value="MALE">Nam</option>
            <option value="FEMALE">Nữ</option>
            <option value="OTHER">Khác</option>
          </select>
        </Field>
      </div>
      <Field label="Ngày sinh" error={form.errors.dateOfBirth}><input {...form.bind('dateOfBirth', 'date')} /></Field>
      <Field label="Địa chỉ" error={form.errors.address}><input {...form.bind('address')} /></Field>
      <Field label="Link ảnh đại diện" error={form.errors.avatarUrl}><input {...form.bind('avatarUrl', 'url')} /></Field>
      <button className="btn" disabled={form.submitting}>Lưu thay đổi</button>
    </form>
  );
}

function PasswordForm() {
  const { clearSession } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const form = useForm({ currentPassword: '', newPassword: '' });

  const onSubmit = async (e) => {
    e.preventDefault();
    try {
      await form.submit((v) => api('/api/users/me/password', { method: 'PUT', body: v }));
      // Backend đã thu hồi mọi phiên đăng nhập
      clearSession();
      toast('Đã đổi mật khẩu, hãy đăng nhập lại', 'success');
      navigate('/login');
    } catch (err) {
      toast(err);
    }
  };

  return (
    <form className="card" onSubmit={onSubmit}>
      <h2 style={{ marginBottom: 16 }}>Đổi mật khẩu</h2>
      <Field label="Mật khẩu hiện tại" error={form.errors.currentPassword}>
        <input {...form.bind('currentPassword', 'password')} autoComplete="current-password" required />
      </Field>
      <Field label="Mật khẩu mới" error={form.errors.newPassword} hint="Đổi xong sẽ đăng xuất khỏi mọi thiết bị">
        <input {...form.bind('newPassword', 'password')} autoComplete="new-password" required />
      </Field>
      <button className="btn" disabled={form.submitting}>Đổi mật khẩu</button>
    </form>
  );
}

function TrainerProfileForm({ trainerId }) {
  const toast = useToast();
  const form = useForm({ specialization: '', bio: '', experienceYears: 0, certifications: '' });
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    api(`/api/trainers/${trainerId}`)
      .then(({ profile }) => form.setValues({
        specialization: profile.specialization || '', bio: profile.bio || '',
        experienceYears: profile.experienceYears ?? 0, certifications: profile.certifications || '',
      }))
      .catch(() => {})
      .finally(() => setLoaded(true));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [trainerId]);

  const onSubmit = async (e) => {
    e.preventDefault();
    try {
      await form.submit((v) => api('/api/trainers/me', {
        method: 'PUT', body: { ...v, experienceYears: Number(v.experienceYears) },
      }));
      toast('Đã lưu hồ sơ huấn luyện viên', 'success');
    } catch (err) {
      toast(err);
    }
  };

  if (!loaded) return null;
  return (
    <form className="card" onSubmit={onSubmit}>
      <h2 style={{ marginBottom: 16 }}>Hồ sơ huấn luyện viên</h2>
      <Field label="Chuyên môn" error={form.errors.specialization}><input {...form.bind('specialization')} /></Field>
      <Field label="Số năm kinh nghiệm" error={form.errors.experienceYears}>
        <input {...form.bind('experienceYears', 'number')} min={0} max={60} />
      </Field>
      <Field label="Chứng chỉ" error={form.errors.certifications}><input {...form.bind('certifications')} /></Field>
      <Field label="Giới thiệu" error={form.errors.bio}><textarea {...form.bind('bio')} /></Field>
      <button className="btn" disabled={form.submitting}>Lưu</button>
    </form>
  );
}
