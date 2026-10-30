import { useEffect, useState } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import { api } from '../../api/client.js';
import { useAuth } from '../../auth/AuthContext.jsx';
import { PageHead, useToast } from '../../components/ui.jsx';
import { WEEKDAYS } from '../../utils/format.js';

/** HLV khai báo ca làm việc lặp lại hàng tuần; hội viên chỉ đặt PT được trong các ca này */
export default function WeeklySchedulePage() {
  const { user } = useAuth();
  const toast = useToast();
  const [items, setItems] = useState(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api(`/api/trainers/${user.id}`)
      .then((r) => setItems(r.schedule.map((s) => ({ ...s, startTime: s.startTime.slice(0, 5), endTime: s.endTime.slice(0, 5) }))))
      .catch((err) => { toast(err); setItems([]); });
  }, [user.id, toast]);

  const update = (i, patch) => setItems(items.map((it, idx) => (idx === i ? { ...it, ...patch } : it)));

  const save = async () => {
    setBusy(true);
    try {
      await api('/api/trainers/me/schedule', {
        method: 'PUT', body: { items: items.map((i) => ({ ...i, dayOfWeek: Number(i.dayOfWeek) })) },
      });
      toast('Đã lưu lịch làm việc', 'success');
    } catch (err) {
      toast(err);
    } finally {
      setBusy(false);
    }
  };

  if (!items) return <div className="spinner" />;
  const sorted = items.map((it, i) => ({ ...it, i })).sort((a, b) => a.dayOfWeek - b.dayOfWeek || a.startTime.localeCompare(b.startTime));

  return (
    <>
      <PageHead title="Lịch làm việc hàng tuần" subtitle="Mỗi ca được chia thành các buổi PT 60 phút">
        <button className="btn secondary" onClick={() => setItems([...items, { dayOfWeek: 1, startTime: '08:00', endTime: '12:00' }])}>
          <Plus size={16} /> Thêm ca
        </button>
        <button className="btn" disabled={busy} onClick={save}>Lưu lịch</button>
      </PageHead>
      <div className="card" style={{ maxWidth: 640 }}>
        {sorted.length === 0 && <div className="empty">Chưa có ca làm việc nào (hội viên sẽ không đặt PT được)</div>}
        {sorted.map(({ i, ...it }) => (
          <div key={i} className="row" style={{ marginBottom: 10 }}>
            <select className="input" style={{ width: 140 }} value={it.dayOfWeek} onChange={(e) => update(i, { dayOfWeek: Number(e.target.value) })}>
              {WEEKDAYS.slice(1).map((d, idx) => <option key={d} value={idx + 1}>{d}</option>)}
            </select>
            <input type="time" className="input" style={{ width: 130 }} value={it.startTime} onChange={(e) => update(i, { startTime: e.target.value })} />
            <span>→</span>
            <input type="time" className="input" style={{ width: 130 }} value={it.endTime} onChange={(e) => update(i, { endTime: e.target.value })} />
            <button className="btn ghost" onClick={() => setItems(items.filter((_, idx) => idx !== i))} aria-label="Xóa ca"><Trash2 size={16} /></button>
          </div>
        ))}
        <p className="muted small">Lịch PT đã được đặt trước đó vẫn giữ nguyên khi đổi lịch làm việc.</p>
      </div>
    </>
  );
}
