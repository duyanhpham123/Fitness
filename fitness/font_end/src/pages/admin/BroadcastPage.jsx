import { api } from '../../api/client.js';
import { Field, PageHead, useForm, useToast } from '../../components/ui.jsx';
import { label } from '../../utils/format.js';

export default function BroadcastPage() {
  const toast = useToast();
  const form = useForm({ title: '', content: '', type: 'PROMOTION', role: 'MEMBER' });

  const send = async (e) => {
    e.preventDefault();
    try {
      const res = await form.submit((v) => api('/api/notifications/broadcast', {
        method: 'POST', body: { ...v, role: v.role || null },
      }));
      toast(`Đã gửi tới ${res.count} người`, 'success');
      form.setValues({ ...form.values, title: '', content: '' });
    } catch (err) {
      toast(err);
    }
  };

  return (
    <>
      <PageHead title="Gửi thông báo" subtitle="Thông báo hiển thị trong app của người nhận" />
      <form className="card" style={{ maxWidth: 640 }} onSubmit={send}>
        <div className="grid grid-2">
          <Field label="Loại">
            <select {...form.bind('type')}>
              {['PROMOTION', 'SYSTEM', 'MEMBERSHIP'].map((t) => <option key={t} value={t}>{label(t)}</option>)}
            </select>
          </Field>
          <Field label="Người nhận">
            <select {...form.bind('role')}>
              <option value="MEMBER">Tất cả hội viên</option>
              <option value="TRAINER">Tất cả huấn luyện viên</option>
              <option value="STAFF">Tất cả lễ tân</option>
              <option value="">Mọi tài khoản</option>
            </select>
          </Field>
        </div>
        <Field label="Tiêu đề" error={form.errors.title}><input {...form.bind('title')} placeholder="Ưu đãi tháng 11" /></Field>
        <Field label="Nội dung" error={form.errors.content}><textarea {...form.bind('content')} rows={4} /></Field>
        <button className="btn" disabled={form.submitting}>Gửi</button>
      </form>
    </>
  );
}
