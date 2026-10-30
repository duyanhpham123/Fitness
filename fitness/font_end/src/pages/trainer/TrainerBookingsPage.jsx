import { useState } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { api } from '../../api/client.js';
import { Async, Avatar, Badge, PageHead, useFetch } from '../../components/ui.jsx';
import { addDays, formatDate, formatTime, toDateInput } from '../../utils/format.js';
import { AttendanceButtons } from '../admin/SchedulePage.jsx';

/** Lịch PT hội viên đặt với HLV trong 7 ngày; sau giờ tập thì điểm danh đã tập / vắng */
export default function TrainerBookingsPage() {
  const [from, setFrom] = useState(toDateInput());
  const to = addDays(from, 6);
  const list = useFetch(() => api('/api/bookings/trainer/me', { query: { from, to } }), [from]);

  // Gom theo ngày
  const byDay = (list.data || []).reduce((acc, b) => {
    const day = b.startAt.slice(0, 10);
    (acc[day] = acc[day] || []).push(b);
    return acc;
  }, {});

  return (
    <>
      <PageHead title="Lịch dạy" subtitle={`${formatDate(from)} - ${formatDate(to)}`}>
        <button className="btn secondary sm" onClick={() => setFrom(addDays(from, -7))}><ChevronLeft size={16} /></button>
        <button className="btn secondary sm" onClick={() => setFrom(toDateInput())}>Hôm nay</button>
        <button className="btn secondary sm" onClick={() => setFrom(addDays(from, 7))}><ChevronRight size={16} /></button>
      </PageHead>
      <Async state={list} empty="Chưa có hội viên đặt lịch PT trong tuần này">
        {() => Object.entries(byDay).map(([day, bookings]) => (
          <div key={day} className="card">
            <h2 style={{ marginBottom: 8 }}>
              {new Date(`${day}T00:00:00`).toLocaleDateString('vi-VN', { weekday: 'long', day: '2-digit', month: '2-digit' })}
            </h2>
            {bookings.map((b) => (
              <div key={b.id} className="list-item">
                <b style={{ width: 110 }}>{formatTime(b.startAt)} - {formatTime(b.endAt)}</b>
                <Avatar name={b.memberName} />
                <div style={{ flex: 1 }}>
                  {b.memberName}
                  {b.note && <div className="muted small">Ghi chú: {b.note}</div>}
                </div>
                <Badge status={b.status} />
                <AttendanceButtons booking={b} onChange={list.reload} />
              </div>
            ))}
          </div>
        ))}
      </Async>
    </>
  );
}
