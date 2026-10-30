import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import {
  Bell, CalendarCheck, CalendarDays, ClipboardList, CreditCard, Dumbbell, FileClock, HeartPulse, Home,
  LayoutDashboard, LogOut, Menu, Package, QrCode, ScanLine, Send, UserCog, UserRound, Users,
} from 'lucide-react';
import { useAuth } from '../auth/AuthContext.jsx';
import { api } from '../api/client.js';
import { label } from '../utils/format.js';
import { Avatar } from './ui.jsx';

// Menu theo vai trò; tài khoản nhiều vai trò thấy các nhóm của tất cả vai trò
const MENUS = [
  {
    title: 'Quản trị',
    roles: ['ADMIN', 'STAFF'],
    items: [
      { to: '/admin', icon: LayoutDashboard, text: 'Tổng quan', end: true },
      { to: '/admin/check-in', icon: ScanLine, text: 'Check-in' },
      { to: '/admin/members', icon: Users, text: 'Hội viên' },
      { to: '/admin/schedule', icon: CalendarDays, text: 'Lịch & lớp học' },
      { to: '/admin/payments', icon: CreditCard, text: 'Thanh toán' },
      { to: '/admin/packages', icon: Package, text: 'Gói tập', roles: ['ADMIN'] },
      { to: '/admin/users', icon: UserCog, text: 'Tài khoản', roles: ['ADMIN'] },
      { to: '/admin/broadcast', icon: Send, text: 'Gửi thông báo', roles: ['ADMIN'] },
      { to: '/admin/audit-logs', icon: FileClock, text: 'Nhật ký thao tác', roles: ['ADMIN'] },
    ],
  },
  {
    title: 'Huấn luyện viên',
    roles: ['TRAINER'],
    items: [
      { to: '/trainer', icon: CalendarCheck, text: 'Lịch dạy', end: true },
      { to: '/trainer/schedule', icon: CalendarDays, text: 'Lịch làm việc' },
      { to: '/trainer/plans', icon: ClipboardList, text: 'Kế hoạch tập' },
    ],
  },
  {
    title: 'Hội viên',
    roles: ['MEMBER'],
    items: [
      { to: '/member', icon: Home, text: 'Trang chủ', end: true },
      { to: '/member/packages', icon: Package, text: 'Gói tập' },
      { to: '/member/booking', icon: CalendarDays, text: 'Đặt lịch' },
      { to: '/member/bookings', icon: CalendarCheck, text: 'Lịch của tôi' },
      { to: '/member/workout', icon: Dumbbell, text: 'Kế hoạch tập' },
      { to: '/member/progress', icon: HeartPulse, text: 'Chỉ số cơ thể' },
      { to: '/member/qr', icon: QrCode, text: 'QR check-in' },
      { to: '/member/payments', icon: CreditCard, text: 'Lịch sử thanh toán' },
    ],
  },
];

export default function Layout() {
  const { user, hasRole, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [open, setOpen] = useState(false);
  const [unread, setUnread] = useState(0);

  // Đếm thông báo chưa đọc, làm mới mỗi phút và mỗi lần chuyển trang
  useEffect(() => {
    const load = () => api('/api/notifications/me/unread-count').then((r) => setUnread(r.count)).catch(() => {});
    load();
    const timer = setInterval(load, 60_000);
    return () => clearInterval(timer);
  }, [location.pathname]);

  useEffect(() => setOpen(false), [location.pathname]);

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <div className="app">
      <aside className={`sidebar${open ? ' open' : ''}`}>
        <div className="brand">
          <Dumbbell size={30} />
          <div>
            <strong>FITNESS</strong>
            <small>Stronger Every Day</small>
          </div>
        </div>
        <nav className="nav">
          {MENUS.filter((m) => hasRole(...m.roles)).map((menu) => (
            <div key={menu.title}>
              <div className="nav-section">{menu.title}</div>
              {menu.items.filter((i) => !i.roles || hasRole(...i.roles)).map((item) => (
                <NavLink key={item.to} to={item.to} end={item.end}>
                  <item.icon size={18} /> {item.text}
                </NavLink>
              ))}
            </div>
          ))}
          <div className="nav-section">Tài khoản</div>
          <NavLink to="/notifications"><Bell size={18} /> Thông báo</NavLink>
          <NavLink to="/profile"><UserRound size={18} /> Hồ sơ</NavLink>
          <a href="#logout" onClick={(e) => { e.preventDefault(); handleLogout(); }}>
            <LogOut size={18} /> Đăng xuất
          </a>
        </nav>
        <div className="sidebar-user row">
          <Avatar name={user.fullName} src={user.avatarUrl} />
          <div>
            {user.fullName}
            <small>{user.roles.map(label).join(', ')}</small>
          </div>
        </div>
      </aside>

      <div className="main">
        <header className="topbar">
          <button className="btn secondary sm menu-btn" onClick={() => setOpen(!open)} aria-label="Menu">
            <Menu size={18} />
          </button>
          <NavLink to="/notifications" className="bell" aria-label="Thông báo">
            <Bell size={20} />
            {unread > 0 && <span className="dot">{unread > 99 ? '99+' : unread}</span>}
          </NavLink>
          <div className="row">
            <Avatar name={user.fullName} src={user.avatarUrl} />
            <span>{user.fullName}</span>
          </div>
        </header>
        <main className="content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
