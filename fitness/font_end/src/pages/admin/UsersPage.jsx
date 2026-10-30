import { useState } from 'react';
import { Plus } from 'lucide-react';
import { api } from '../../api/client.js';
import { useAuth } from '../../auth/AuthContext.jsx';
import { Async, Avatar, Badge, Field, Modal, PageHead, Pagination, confirmAction, useFetch, useForm, useToast } from '../../components/ui.jsx';
import { formatDate, label } from '../../utils/format.js';

const ROLES = ['ADMIN', 'STAFF', 'TRAINER', 'MEMBER'];

export default function UsersPage() {
  const toast = useToast();
  const { user: me } = useAuth();
  const [filter, setFilter] = useState({ role: '', status: '', keyword: '', page: 0 });
  const [keyword, setKeyword] = useState('');
  const [modal, setModal] = useState(null);
  const list = useFetch(() => api('/api/admin/users', { query: { ...filter, size: 15 } }), [filter]);

  const toggleLock = async (u) => {
    const lock = u.status !== 'LOCKED';
    if (lock && !(await confirmAction(`Khóa tài khoản ${u.fullName}? Người này sẽ bị đăng xuất khỏi mọi thiết bị.`))) return;
    try {
      await api(`/api/admin/users/${u.id}/status`, { method: 'PUT', body: { status: lock ? 'LOCKED' : 'ACTIVE' } });
      toast(lock ? 'Đã khóa tài khoản' : 'Đã mở khóa', 'success');
      list.reload();
    } catch (err) {
      toast(err);
    }
  };

  return (
    <>
      <PageHead title="Tài khoản">
        <button className="btn" onClick={() => setModal('create')}><Plus size={16} /> Tạo tài khoản</button>
      </PageHead>
      <div className="card">
        <form className="row" style={{ marginBottom: 14 }}
          onSubmit={(e) => { e.preventDefault(); setFilter({ ...filter, keyword, page: 0 }); }}>
          <input className="input" style={{ maxWidth: 280 }} value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder="Tên, email, SĐT" />
          <select className="input" style={{ width: 160 }} value={filter.role} onChange={(e) => setFilter({ ...filter, role: e.target.value, page: 0 })}>
            <option value="">Mọi vai trò</option>
            {ROLES.map((r) => <option key={r} value={r}>{label(r)}</option>)}
          </select>
          <select className="input" style={{ width: 160 }} value={filter.status} onChange={(e) => setFilter({ ...filter, status: e.target.value, page: 0 })}>
            <option value="">Mọi trạng thái</option>
            {['ACTIVE', 'PENDING', 'LOCKED'].map((s) => <option key={s} value={s}>{label(s)}</option>)}
          </select>
          <button className="btn secondary">Tìm</button>
        </form>
        <Async state={list} empty="Không có tài khoản">
          {(page) => (
            <>
              <div className="table-wrap">
                <table>
                  <thead><tr><th>Tài khoản</th><th>SĐT</th><th>Vai trò</th><th>Trạng thái</th><th>Tạo ngày</th><th /></tr></thead>
                  <tbody>
                    {page.content.map((u) => (
                      <tr key={u.id}>
                        <td><div className="row"><Avatar name={u.fullName} src={u.avatarUrl} /><div>{u.fullName}<div className="muted small">{u.email}</div></div></div></td>
                        <td>{u.phone || '-'}</td>
                        <td>{u.roles.map((r) => <Badge key={r} color="blue">{label(r)}</Badge>)}</td>
                        <td><Badge status={u.status} /></td>
                        <td>{formatDate(u.createdAt)}</td>
                        <td className="right">
                          {u.id !== me.id && (
                            <div className="row" style={{ justifyContent: 'flex-end' }}>
                              <button className="btn secondary sm" onClick={() => setModal({ roles: u })}>Vai trò</button>
                              <button className={`btn sm ${u.status === 'LOCKED' ? 'success' : 'danger'}`} onClick={() => toggleLock(u)}>
                                {u.status === 'LOCKED' ? 'Mở khóa' : 'Khóa'}
                              </button>
                            </div>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <Pagination page={page} onChange={(p) => setFilter({ ...filter, page: p })} />
            </>
          )}
        </Async>
      </div>
      {modal === 'create' && <CreateUserModal onClose={() => setModal(null)} onSaved={list.reload} />}
      {modal?.roles && <RolesModal user={modal.roles} onClose={() => setModal(null)} onSaved={list.reload} />}
    </>
  );
}

function CreateUserModal({ onClose, onSaved }) {
  const toast = useToast();
  const form = useForm({ fullName: '', email: '', phone: '', password: '', role: 'STAFF', specialization: '' });
  const save = async () => {
    try {
      await form.submit((v) => api('/api/admin/users', {
        method: 'POST', body: { ...v, phone: v.phone || null, specialization: v.specialization || null },
      }));
      toast('Đã tạo tài khoản', 'success');
      onSaved();
      onClose();
    } catch (err) {
      toast(err);
    }
  };
  return (
    <Modal title="Tạo tài khoản" onClose={onClose}
      footer={<><button className="btn secondary" onClick={onClose}>Đóng</button><button className="btn" disabled={form.submitting} onClick={save}>Tạo</button></>}>
      <Field label="Họ tên" error={form.errors.fullName}><input {...form.bind('fullName')} /></Field>
      <div className="grid grid-2">
        <Field label="Email" error={form.errors.email}><input {...form.bind('email', 'email')} /></Field>
        <Field label="Số điện thoại" error={form.errors.phone}><input {...form.bind('phone', 'tel')} /></Field>
      </div>
      <div className="grid grid-2">
        <Field label="Mật khẩu ban đầu" error={form.errors.password}><input {...form.bind('password', 'password')} autoComplete="new-password" /></Field>
        <Field label="Vai trò" error={form.errors.role}>
          <select {...form.bind('role')}>{ROLES.map((r) => <option key={r} value={r}>{label(r)}</option>)}</select>
        </Field>
      </div>
      {form.values.role === 'TRAINER' && (
        <Field label="Chuyên môn" error={form.errors.specialization}><input {...form.bind('specialization')} placeholder="Gym, Yoga..." /></Field>
      )}
    </Modal>
  );
}

function RolesModal({ user, onClose, onSaved }) {
  const toast = useToast();
  const [roles, setRoles] = useState(user.roles);
  const [busy, setBusy] = useState(false);
  const toggle = (r) => setRoles(roles.includes(r) ? roles.filter((x) => x !== r) : [...roles, r]);
  const save = async () => {
    setBusy(true);
    try {
      await api(`/api/admin/users/${user.id}/roles`, { method: 'PUT', body: { roles } });
      toast('Đã cập nhật vai trò, người dùng cần đăng nhập lại', 'success');
      onSaved();
      onClose();
    } catch (err) {
      toast(err);
    } finally {
      setBusy(false);
    }
  };
  return (
    <Modal title={`Vai trò của ${user.fullName}`} onClose={onClose}
      footer={<><button className="btn secondary" onClick={onClose}>Đóng</button><button className="btn" disabled={busy || roles.length === 0} onClick={save}>Lưu</button></>}>
      {ROLES.map((r) => (
        <label key={r} className="checkbox" style={{ marginBottom: 10 }}>
          <input type="checkbox" checked={roles.includes(r)} onChange={() => toggle(r)} /> {label(r)}
        </label>
      ))}
      <p className="muted small">Thêm vai trò Huấn luyện viên sẽ tạo hồ sơ HLV; bỏ đi sẽ ẩn HLV khỏi danh sách đặt lịch.</p>
    </Modal>
  );
}
