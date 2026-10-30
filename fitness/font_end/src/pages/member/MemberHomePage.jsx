import { Link } from 'react-router-dom';
import { CalendarDays, CreditCard, Dumbbell, QrCode } from 'lucide-react';
import { api } from '../../api/client.js';
import { useAuth } from '../../auth/AuthContext.jsx';
import { Async, Badge, useFetch } from '../../components/ui.jsx';
import { formatDate, formatDateTime, label, toDateInput } from '../../utils/format.js';

export default function MemberHomePage() {
  const { user } = useAuth();
  const subs = useFetch(() => api('/api/subscriptions/me'), []);
  const bookings = useFetch(() => api('/api/bookings/me', { query: { size: 20 } }), []);

  const today = toDateInput();
  const current = subs.data?.find((s) => ['ACTIVE', 'FROZEN'].includes(s.status) && s.startDate <= today && s.endDate >= today);
  const upcoming = (bookings.data?.content || [])
    .filter((b) => b.status === 'CONFIRMED' && new Date(b.startAt) > new Date())
    .sort((a, b) => a.startAt.localeCompare(b.startAt))
    .slice(0, 5);

  return (
    <>
      <div className="card" style={{ background: 'linear-gradient(135deg, #0f2747, #2563eb)', color: '#fff', marginBottom: 16 }}>
        <div className="muted" style={{ color: '#cbd5e1' }}>Xin chào,</div>
        <h1 style={{ margin: '4px 0 8px' }}>{user.fullName}</h1>
        <p style={{ color: '#dbeafe', margin: 0 }}>Tập luyện vì một phiên bản tốt hơn của bạn</p>
      </div>

      <div className="grid grid-4" style={{ marginBottom: 16 }}>
        {[
          ['/member/booking', CalendarDays, 'Đặt lịch'],
          ['/member/packages', CreditCard, 'Gói tập'],
          ['/member/workout', Dumbbell, 'Kế hoạch tập'],
          ['/member/qr', QrCode, 'QR check-in'],
        ].map(([to, Icon, text]) => (
          <Link key={to} to={to} className="card row" style={{ color: 'inherit', justifyContent: 'center' }}>
            <Icon size={20} color="var(--primary)" /> {text}
          </Link>
        ))}
      </div>

      <div className="grid grid-2">
        <div className="card">
          <div className="card-head"><h2>Gói tập hiện tại</h2><Link to="/member/packages">Chi tiết →</Link></div>
          <Async state={subs} isEmpty={() => !current} empty="Bạn chưa có gói tập còn hiệu lực">
            {() => (
              <div className="stack">
                <div className="row"><h3>{current.packageName}</h3><Badge status={current.status} /></div>
                <div className="muted">Từ {formatDate(current.startDate)} đến <b>{formatDate(current.endDate)}</b></div>
                <div>Buổi PT còn lại: <b>{current.ptSessionsRemaining}</b></div>
              </div>
            )}
          </Async>
        </div>
        <div className="card">
          <div className="card-head"><h2>Lịch tập sắp tới</h2><Link to="/member/bookings">Xem tất cả →</Link></div>
          <Async state={bookings} isEmpty={() => upcoming.length === 0} empty="Chưa có lịch sắp tới">
            {() => upcoming.map((b) => (
              <div key={b.id} className="list-item">
                <div style={{ flex: 1 }}>
                  {b.type === 'PT' ? `PT với ${b.trainerName}` : b.className}
                  <div className="muted small">{formatDateTime(b.startAt)}</div>
                </div>
                <Badge color="blue">{label(b.type)}</Badge>
              </div>
            ))}
          </Async>
        </div>
      </div>
    </>
  );
}
