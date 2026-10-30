import { useState } from 'react';
import { ChevronLeft, ChevronRight, Plus } from 'lucide-react';
import { api } from '../../api/client.js';
import { Async, Badge, Field, Modal, PageHead, useFetch, useForm, useToast } from '../../components/ui.jsx';
import { addDays, formatDate, formatTime, label, toDateInput } from '../../utils/format.js';

/** Lịch theo ngày: buổi học nhóm (mở / hủy) và mọi lịch hẹn (điểm danh đã tập / vắng). */
export default function SchedulePage() {
  const [date, setDate] = useState(toDateInput());
  const [modal, setModal] = useState(null);
  const sessions = useFetch(() => api('/api/class-sessions', { query: { date } }), [date]);
  const bookings = useFetch(() => api('/api/reports/bookings', { query: { date } }), [date]);

  const refresh = () => {
    sessions.reload();
    bookings.reload();
  };

  return (
    <>
      <PageHead title="Lịch & lớp học" subtitle={formatDate(date)}>
        <button className="btn secondary sm" onClick={() => setDate(addDays(date, -1))}><ChevronLeft size={16} /></button>
        <input type="date" className="input" style={{ width: 160 }} value={date} onChange={(e) => setDate(e.target.value)} />
        <button className="btn secondary sm" onClick={() => setDate(addDays(date, 1))}><ChevronRight size={16} /></button>
        <button className="btn secondary" onClick={() => setModal('class')}>Tạo lớp học</button>
        <button className="btn" onClick={() => setModal('session')}><Plus size={16} /> Mở buổi học</button>
      </PageHead>

      <div className="card">
        <div className="card-head"><h2>Buổi học nhóm</h2></div>
        <Async state={sessions} empty="Ngày này chưa có buổi học">
          {(list) => (
            <div className="table-wrap">
              <table>
                <thead><tr><th>Giờ</th><th>Lớp</th><th>HLV</th><th>Phòng</th><th>Chỗ</th><th>Trạng thái</th><th /></tr></thead>
                <tbody>
                  {list.map((s) => (
                    <tr key={s.id}>
                      <td>{formatTime(s.startAt)} - {formatTime(s.endAt)}</td>
                      <td>{s.className}</td>
                      <td>{s.trainerName}</td>
                      <td>{s.room || '-'}</td>
                      <td>{s.bookedCount}/{s.capacity}</td>
                      <td><Badge status={s.status} /></td>
                      <td className="right">
                        {s.status === 'SCHEDULED' && new Date(s.startAt) > new Date() && (
                          <button className="btn secondary sm" onClick={() => setModal({ cancel: s })}>Hủy buổi</button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Async>
      </div>

      <div className="card">
        <div className="card-head"><h2>Lịch hẹn trong ngày</h2></div>
        <Async state={bookings} empty="Không có lịch hẹn">
          {(list) => (
            <div className="table-wrap">
              <table>
                <thead><tr><th>Giờ</th><th>Hội viên</th><th>Loại</th><th>Chi tiết</th><th>Trạng thái</th><th /></tr></thead>
                <tbody>
                  {list.map((b) => <BookingRow key={b.id} booking={b} onChange={bookings.reload} />)}
                </tbody>
              </table>
            </div>
          )}
        </Async>
      </div>

      {modal === 'session' && <SessionModal date={date} onClose={() => setModal(null)} onDone={refresh} />}
      {modal === 'class' && <ClassModal onClose={() => setModal(null)} />}
      {modal?.cancel && <CancelModal session={modal.cancel} onClose={() => setModal(null)} onDone={refresh} />}
    </>
  );
}

export function AttendanceButtons({ booking, onChange }) {
  const toast = useToast();
  if (booking.status !== 'CONFIRMED' || new Date(booking.startAt) > new Date()) return null;
  const mark = async (status) => {
    try {
      await api(`/api/bookings/${booking.id}/attendance`, { method: 'PUT', body: { status } });
      toast(status === 'COMPLETED' ? 'Đã đánh dấu đã tập' : 'Đã đánh dấu vắng mặt', 'success');
      onChange();
    } catch (err) {
      toast(err);
    }
  };
  return (
    <div className="row" style={{ justifyContent: 'flex-end' }}>
      <button className="btn success sm" onClick={() => mark('COMPLETED')}>Đã tập</button>
      <button className="btn secondary sm" onClick={() => mark('NO_SHOW')}>Vắng</button>
    </div>
  );
}

function BookingRow({ booking: b, onChange }) {
  return (
    <tr>
      <td>{formatTime(b.startAt)} - {formatTime(b.endAt)}</td>
      <td>{b.memberName}</td>
      <td>{label(b.type)}</td>
      <td>{b.type === 'PT' ? `HLV ${b.trainerName}` : `${b.className}${b.room ? ` · ${b.room}` : ''}`}</td>
      <td><Badge status={b.status} /></td>
      <td><AttendanceButtons booking={b} onChange={onChange} /></td>
    </tr>
  );
}

function SessionModal({ date, onClose, onDone }) {
  const toast = useToast();
  const classes = useFetch(() => api('/api/classes'), []);
  const trainers = useFetch(() => api('/api/trainers'), []);
  const form = useForm({ classId: '', trainerId: '', startAt: `${date}T08:00`, room: '', capacity: '' });

  const save = async () => {
    try {
      await form.submit((v) => api('/api/class-sessions', {
        method: 'POST',
        body: {
          classId: Number(v.classId), trainerId: Number(v.trainerId), startAt: v.startAt,
          room: v.room || null, capacity: v.capacity ? Number(v.capacity) : null,
        },
      }));
      toast('Đã mở buổi học', 'success');
      onDone();
      onClose();
    } catch (err) {
      toast(err);
    }
  };

  return (
    <Modal title="Mở buổi học" onClose={onClose}
      footer={<><button className="btn secondary" onClick={onClose}>Đóng</button><button className="btn" disabled={form.submitting} onClick={save}>Lưu</button></>}>
      <Field label="Lớp học" error={form.errors.classId}>
        <select {...form.bind('classId')}>
          <option value="">-- Chọn lớp --</option>
          {classes.data?.map((c) => <option key={c.id} value={c.id}>{c.name} ({c.durationMinutes} phút)</option>)}
        </select>
      </Field>
      <Field label="Huấn luyện viên" error={form.errors.trainerId}>
        <select {...form.bind('trainerId')}>
          <option value="">-- Chọn HLV --</option>
          {trainers.data?.map((t) => <option key={t.id} value={t.id}>{t.fullName}</option>)}
        </select>
      </Field>
      <Field label="Bắt đầu" error={form.errors.startAt}><input {...form.bind('startAt', 'datetime-local')} /></Field>
      <div className="grid grid-2">
        <Field label="Phòng" error={form.errors.room}><input {...form.bind('room')} placeholder="Phòng A" /></Field>
        <Field label="Sức chứa" error={form.errors.capacity} hint="Bỏ trống = theo lớp"><input {...form.bind('capacity', 'number')} min={1} /></Field>
      </div>
    </Modal>
  );
}

function ClassModal({ onClose }) {
  const toast = useToast();
  const form = useForm({ name: '', description: '', durationMinutes: 60, defaultCapacity: 20, imageUrl: '' });
  const save = async () => {
    try {
      await form.submit((v) => api('/api/classes', {
        method: 'POST',
        body: { ...v, durationMinutes: Number(v.durationMinutes), defaultCapacity: Number(v.defaultCapacity), imageUrl: v.imageUrl || null },
      }));
      toast('Đã tạo lớp học', 'success');
      onClose();
    } catch (err) {
      toast(err);
    }
  };
  return (
    <Modal title="Tạo lớp học" onClose={onClose}
      footer={<><button className="btn secondary" onClick={onClose}>Đóng</button><button className="btn" disabled={form.submitting} onClick={save}>Lưu</button></>}>
      <Field label="Tên lớp" error={form.errors.name}><input {...form.bind('name')} placeholder="Pilates" /></Field>
      <Field label="Mô tả" error={form.errors.description}><textarea {...form.bind('description')} /></Field>
      <div className="grid grid-2">
        <Field label="Thời lượng (phút)" error={form.errors.durationMinutes}><input {...form.bind('durationMinutes', 'number')} /></Field>
        <Field label="Sức chứa mặc định" error={form.errors.defaultCapacity}><input {...form.bind('defaultCapacity', 'number')} /></Field>
      </div>
    </Modal>
  );
}

function CancelModal({ session, onClose, onDone }) {
  const toast = useToast();
  const form = useForm({ reason: '' });
  const save = async () => {
    try {
      await form.submit((v) => api(`/api/class-sessions/${session.id}/cancel`, { method: 'POST', body: v }));
      toast('Đã hủy buổi học và báo cho hội viên', 'success');
      onDone();
      onClose();
    } catch (err) {
      toast(err);
    }
  };
  return (
    <Modal title={`Hủy buổi ${session.className} lúc ${formatTime(session.startAt)}`} onClose={onClose}
      footer={<><button className="btn secondary" onClick={onClose}>Đóng</button><button className="btn danger" disabled={form.submitting} onClick={save}>Hủy buổi học</button></>}>
      <div className="alert info">{session.bookedCount} hội viên đã đặt chỗ sẽ nhận thông báo kèm lý do.</div>
      <Field label="Lý do" error={form.errors.reason}><input {...form.bind('reason')} placeholder="HLV bị ốm" /></Field>
    </Modal>
  );
}
