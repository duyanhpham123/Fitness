import { Link } from 'react-router-dom';
import { CalendarCheck, Package, Users, Wallet } from 'lucide-react';
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { api } from '../../api/client.js';
import { useAuth } from '../../auth/AuthContext.jsx';
import { Async, Avatar, Badge, PageHead, StatCard, useFetch } from '../../components/ui.jsx';
import { formatDate, formatMoney, formatTime, toDateInput } from '../../utils/format.js';

export default function DashboardPage() {
  const { hasRole } = useAuth();
  const isAdmin = hasRole('ADMIN');
  const today = new Date().toLocaleDateString('vi-VN', { weekday: 'long', day: '2-digit', month: '2-digit', year: 'numeric' });
  return (
    <>
      <PageHead title="Tổng quan" subtitle={today} />
      {isAdmin && <Overview />}
      <div className="grid grid-2" style={{ marginTop: 16 }}>
        {isAdmin && <RevenueChart />}
        <TodayBookings />
        {isAdmin && <NewMembers />}
        {isAdmin && <TopPackages />}
      </div>
    </>
  );
}

function Overview() {
  const state = useFetch(() => api('/api/reports/overview'), []);
  return (
    <Async state={state}>
      {(o) => (
        <div className="grid grid-4">
          <StatCard icon={Users} color="#2563eb" title="Hội viên" value={Number(o.totalMembers.value).toLocaleString('vi-VN')} change={o.totalMembers.changePercent} />
          <StatCard icon={Wallet} color="#16a34a" title="Doanh thu tháng" value={formatMoney(o.monthlyRevenue.value)} change={o.monthlyRevenue.changePercent} />
          <StatCard icon={CalendarCheck} color="#7c3aed" title="Lượt đặt lịch" value={o.monthlyBookings.value} change={o.monthlyBookings.changePercent} />
          <StatCard icon={Package} color="#ea580c" title="Gói đang hoạt động" value={o.activeSubscriptions.value} change={o.activeSubscriptions.changePercent} />
        </div>
      )}
    </Async>
  );
}

function RevenueChart() {
  const state = useFetch(() => api('/api/reports/revenue'), []);
  return (
    <div className="card">
      <div className="card-head"><h2>Doanh thu 7 ngày gần đây</h2></div>
      <Async state={state}>
        {(r) => (
          <>
            <div className="muted small" style={{ marginBottom: 8 }}>Tổng: <b>{formatMoney(r.total)}</b></div>
            <ResponsiveContainer width="100%" height={240}>
              <LineChart data={r.days.map((d) => ({ ...d, day: formatDate(d.date).slice(0, 5), amount: Number(d.amount) }))}>
                <CartesianGrid strokeDasharray="3 3" stroke="#eef0f4" />
                <XAxis dataKey="day" fontSize={12} />
                <YAxis fontSize={12} tickFormatter={(v) => (v >= 1e6 ? `${v / 1e6}M` : v)} />
                <Tooltip formatter={(v) => formatMoney(v)} />
                <Line type="monotone" dataKey="amount" stroke="#2563eb" strokeWidth={2} dot={{ r: 3 }} />
              </LineChart>
            </ResponsiveContainer>
          </>
        )}
      </Async>
    </div>
  );
}

function TodayBookings() {
  const state = useFetch(() => api('/api/reports/bookings', { query: { date: toDateInput() } }), []);
  return (
    <div className="card">
      <div className="card-head">
        <h2>Lịch hẹn hôm nay</h2>
        <Link to="/admin/schedule">Xem tất cả →</Link>
      </div>
      <Async state={state} empty="Hôm nay chưa có lịch hẹn">
        {(list) => list.map((b) => (
          <div key={b.id} className="list-item">
            <b style={{ width: 100 }}>{formatTime(b.startAt)} - {formatTime(b.endAt)}</b>
            <Avatar name={b.memberName} />
            <div style={{ flex: 1 }}>
              {b.memberName}
              <div className="muted small">{b.type === 'PT' ? `PT - ${b.trainerName}` : b.className}</div>
            </div>
            <Badge status={b.status} />
          </div>
        ))}
      </Async>
    </div>
  );
}

function NewMembers() {
  const state = useFetch(() => api('/api/reports/members/new', { query: { limit: 5 } }), []);
  return (
    <div className="card">
      <div className="card-head">
        <h2>Hội viên mới</h2>
        <Link to="/admin/members">Xem tất cả →</Link>
      </div>
      <Async state={state}>
        {(list) => list.map((m) => (
          <div key={m.id} className="list-item">
            <Avatar name={m.fullName} src={m.avatarUrl} />
            <div style={{ flex: 1 }}>{m.fullName}</div>
            <span className="muted">{m.packageName || 'Chưa mua gói'}</span>
          </div>
        ))}
      </Async>
    </div>
  );
}

function TopPackages() {
  const state = useFetch(() => api('/api/reports/packages/top'), []);
  const attendance = useFetch(() => api('/api/reports/attendance'), []);
  return (
    <div className="card">
      <div className="card-head">
        <h2>Gói bán chạy tháng này</h2>
        {attendance.data?.rate !== null && attendance.data && (
          <Badge color="blue">Tỷ lệ đi tập: {attendance.data.rate}%</Badge>
        )}
      </div>
      <Async state={state} empty="Tháng này chưa bán được gói nào">
        {(list) => (
          <table>
            <thead><tr><th>Gói</th><th className="right">Lượt bán</th><th className="right">Doanh thu</th><th className="right">%</th></tr></thead>
            <tbody>
              {list.map((p) => (
                <tr key={p.packageId}>
                  <td>{p.packageName}</td>
                  <td className="right">{p.sold}</td>
                  <td className="right">{formatMoney(p.revenue)}</td>
                  <td className="right">{p.percent}%</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Async>
    </div>
  );
}
