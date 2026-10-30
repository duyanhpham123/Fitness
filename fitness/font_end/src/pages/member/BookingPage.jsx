import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, newIdempotencyKey } from '../../api/client.js';
import { Async, Avatar, Field, PageHead, useFetch, useToast } from '../../components/ui.jsx';
import { addDays, formatDate, formatMoney, formatTime, toDateInput } from '../../utils/format.js';

export default function BookingPage() {
  const [tab, setTab] = useState('PT');
  return (
    <>
      <PageHead title="Đặt lịch tập" />
      <div className="tabs">
        <button className={tab === 'PT' ? 'active' : ''} onClick={() => setTab('PT')}>PT 1-1</button>
        <button className={tab === 'CLASS' ? 'active' : ''} onClick={() => setTab('CLASS')}>Lớp học nhóm</button>
      </div>
      {tab === 'PT' ? <PtBooking /> : <ClassBooking />}
    </>
  );
}

function DatePicker({ date, setDate }) {
  const today = toDateInput();
  return (
    <div className="row">
      {[0, 1, 2, 3, 4, 5, 6].map((i) => {
        const d = addDays(today, i);
        const dt = new Date(`${d}T00:00:00`);
        return (
          <button key={d} className={`slot${d === date ? ' selected' : ''}`} onClick={() => setDate(d)}>
            {dt.toLocaleDateString('vi-VN', { weekday: 'short' })}<br />{formatDate(d).slice(0, 5)}
          </button>
        );
      })}
      <input type="date" className="input" style={{ width: 160 }} min={today} value={date} onChange={(e) => setDate(e.target.value)} />
    </div>
  );
}

function PtBooking() {
  const toast = useToast();
  const navigate = useNavigate();
  const trainers = useFetch(() => api('/api/trainers'), []);
  const [trainerId, setTrainerId] = useState(null);
  const [date, setDate] = useState(toDateInput());
  const [slot, setSlot] = useState(null);
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  const slots = useFetch(
    () => (trainerId ? api(`/api/trainers/${trainerId}/slots`, { query: { date } }) : Promise.resolve([])),
    [trainerId, date],
  );

  const book = async () => {
    setBusy(true);
    try {
      await api('/api/bookings/pt', {
        method: 'POST', body: { trainerId, startAt: slot, note: note || null },
        headers: { 'Idempotency-Key': newIdempotencyKey() },
      });
      toast('Đặt lịch PT thành công', 'success');
      navigate('/member/bookings');
    } catch (err) {
      toast(err);
      slots.reload();
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="stack">
      <div className="card">
        <h2 style={{ marginBottom: 12 }}>1. Chọn huấn luyện viên</h2>
        <Async state={trainers}>
          {(list) => (
            <div className="grid grid-3">
              {list.map((t) => (
                <button key={t.id} className={`slot${trainerId === t.id ? ' selected' : ''}`} style={{ textAlign: 'left' }}
                  onClick={() => { setTrainerId(t.id); setSlot(null); }}>
                  <div className="row">
                    <Avatar name={t.fullName} src={t.avatarUrl} />
                    <div>
                      <div>{t.fullName}</div>
                      <div className="muted small">{t.specialization} · {t.experienceYears} năm · ★ {t.rating}</div>
                      {t.sessionPrice && <div className="muted small">{formatMoney(t.sessionPrice)}/buổi</div>}
                    </div>
                  </div>
                </button>
              ))}
            </div>
          )}
        </Async>
      </div>
      {trainerId && (
        <div className="card">
          <h2 style={{ marginBottom: 12 }}>2. Chọn ngày và giờ</h2>
          <DatePicker date={date} setDate={(d) => { setDate(d); setSlot(null); }} />
          <div style={{ marginTop: 14 }}>
            <Async state={slots} empty="Ngày này HLV không còn giờ trống">
              {(list) => (
                <div className="slots">
                  {list.map((s) => (
                    <button key={s.startAt} className={`slot${slot === s.startAt ? ' selected' : ''}`} onClick={() => setSlot(s.startAt)}>
                      {formatTime(s.startAt)} - {formatTime(s.endAt)}
                    </button>
                  ))}
                </div>
              )}
            </Async>
          </div>
          {slot && (
            <div style={{ marginTop: 16 }}>
              <Field label="Ghi chú cho HLV (không bắt buộc)">
                <input className="input" value={note} onChange={(e) => setNote(e.target.value)} placeholder="VD: muốn tập lưng" />
              </Field>
              <button className="btn" disabled={busy} onClick={book}>Đặt lịch lúc {formatTime(slot)} {formatDate(slot)}</button>
              <p className="muted small">Mỗi buổi PT trừ 1 buổi trong gói. Có thể hủy trước giờ tập ít nhất 2 giờ.</p>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function ClassBooking() {
  const toast = useToast();
  const [date, setDate] = useState(toDateInput());
  const sessions = useFetch(() => api('/api/class-sessions', { query: { date } }), [date]);

  const book = async (s) => {
    try {
      await api('/api/bookings/class', {
        method: 'POST', body: { classSessionId: s.id }, headers: { 'Idempotency-Key': newIdempotencyKey() },
      });
      toast(`Đã đặt chỗ lớp ${s.className}`, 'success');
      sessions.reload();
    } catch (err) {
      toast(err);
    }
  };

  return (
    <div className="card">
      <DatePicker date={date} setDate={setDate} />
      <div style={{ marginTop: 14 }}>
        <Async state={sessions} empty="Ngày này chưa có lớp học">
          {(list) => list.map((s) => {
            const past = new Date(s.startAt) <= new Date();
            const full = s.availableSeats <= 0;
            return (
              <div key={s.id} className="list-item">
                <b style={{ width: 110 }}>{formatTime(s.startAt)} - {formatTime(s.endAt)}</b>
                <div style={{ flex: 1 }}>
                  {s.className}
                  <div className="muted small">HLV {s.trainerName}{s.room ? ` · ${s.room}` : ''}</div>
                </div>
                <span className={full ? 'down' : 'muted'}>{full ? 'Hết chỗ' : `Còn ${s.availableSeats} chỗ`}</span>
                <button className="btn sm" disabled={past || full || s.status !== 'SCHEDULED'} onClick={() => book(s)}>
                  {s.status === 'CANCELLED' ? 'Đã hủy' : 'Đặt lịch'}
                </button>
              </div>
            );
          })}
        </Async>
      </div>
    </div>
  );
}
