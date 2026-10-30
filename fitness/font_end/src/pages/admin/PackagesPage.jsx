import { useState } from 'react';
import { Plus } from 'lucide-react';
import { api } from '../../api/client.js';
import { Async, Badge, Field, Modal, PageHead, useFetch, useForm, useToast } from '../../components/ui.jsx';
import { formatMoney } from '../../utils/format.js';

const EMPTY = { code: '', name: '', description: '', durationDays: 30, price: 0, ptSessions: 0, maxFreezeDays: 0, active: true };

export default function PackagesPage() {
  const [editing, setEditing] = useState(null);
  const list = useFetch(() => api('/api/packages', { query: { includeInactive: true } }), []);

  return (
    <>
      <PageHead title="Gói tập">
        <button className="btn" onClick={() => setEditing(EMPTY)}><Plus size={16} /> Thêm gói</button>
      </PageHead>
      <div className="card">
        <Async state={list}>
          {(packages) => (
            <div className="table-wrap">
              <table>
                <thead><tr><th>Mã</th><th>Tên gói</th><th>Thời hạn</th><th className="right">Giá</th><th>Buổi PT</th><th>Bảo lưu tối đa</th><th>Trạng thái</th><th /></tr></thead>
                <tbody>
                  {packages.map((p) => (
                    <tr key={p.id}>
                      <td className="small">{p.code}</td>
                      <td>{p.name}<div className="muted small">{p.description}</div></td>
                      <td>{p.durationDays} ngày</td>
                      <td className="right">{formatMoney(p.price)}</td>
                      <td>{p.ptSessions}</td>
                      <td>{p.maxFreezeDays} ngày</td>
                      <td>{p.active ? <Badge color="green">Đang bán</Badge> : <Badge color="gray">Ngừng bán</Badge>}</td>
                      <td className="right"><button className="btn secondary sm" onClick={() => setEditing(p)}>Sửa</button></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Async>
      </div>
      {editing && <PackageModal pkg={editing} onClose={() => setEditing(null)} onSaved={list.reload} />}
    </>
  );
}

function PackageModal({ pkg, onClose, onSaved }) {
  const toast = useToast();
  const form = useForm({ ...pkg });
  const isNew = !pkg.id;

  const save = async () => {
    try {
      await form.submit((v) => api(isNew ? '/api/packages' : `/api/packages/${pkg.id}`, {
        method: isNew ? 'POST' : 'PUT',
        body: {
          code: v.code, name: v.name, description: v.description || null, active: v.active,
          durationDays: Number(v.durationDays), price: Number(v.price),
          ptSessions: Number(v.ptSessions), maxFreezeDays: Number(v.maxFreezeDays),
        },
      }));
      toast(isNew ? 'Đã thêm gói tập' : 'Đã lưu gói tập', 'success');
      onSaved();
      onClose();
    } catch (err) {
      toast(err);
    }
  };

  return (
    <Modal title={isNew ? 'Thêm gói tập' : `Sửa ${pkg.name}`} onClose={onClose}
      footer={<><button className="btn secondary" onClick={onClose}>Đóng</button><button className="btn" disabled={form.submitting} onClick={save}>Lưu</button></>}>
      <div className="grid grid-2">
        <Field label="Mã gói" error={form.errors.code} hint="VD: GYM_2M"><input {...form.bind('code')} /></Field>
        <Field label="Tên gói" error={form.errors.name}><input {...form.bind('name')} /></Field>
      </div>
      <Field label="Mô tả" error={form.errors.description}><textarea {...form.bind('description')} /></Field>
      <div className="grid grid-2">
        <Field label="Thời hạn (ngày)" error={form.errors.durationDays}><input {...form.bind('durationDays', 'number')} min={1} /></Field>
        <Field label="Giá (VNĐ)" error={form.errors.price} hint="Đổi giá không ảnh hưởng gói đã bán"><input {...form.bind('price', 'number')} min={0} step={1000} /></Field>
        <Field label="Số buổi PT tặng" error={form.errors.ptSessions}><input {...form.bind('ptSessions', 'number')} min={0} /></Field>
        <Field label="Bảo lưu tối đa (ngày)" error={form.errors.maxFreezeDays}><input {...form.bind('maxFreezeDays', 'number')} min={0} /></Field>
      </div>
      <label className="checkbox"><input type="checkbox" checked={form.values.active} onChange={(e) => form.set('active', e.target.checked)} /> Đang bán</label>
    </Modal>
  );
}
