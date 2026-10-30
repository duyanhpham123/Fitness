import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { useAuth } from './auth/AuthContext.jsx';
import Layout from './components/Layout.jsx';

import LoginPage from './pages/auth/LoginPage.jsx';
import RegisterPage from './pages/auth/RegisterPage.jsx';
import ForgotPasswordPage from './pages/auth/ForgotPasswordPage.jsx';
import PaymentResultPage from './pages/PaymentResultPage.jsx';
import ProfilePage from './pages/ProfilePage.jsx';
import NotificationsPage from './pages/NotificationsPage.jsx';

import DashboardPage from './pages/admin/DashboardPage.jsx';
import CheckInPage from './pages/admin/CheckInPage.jsx';
import MembersPage from './pages/admin/MembersPage.jsx';
import SchedulePage from './pages/admin/SchedulePage.jsx';
import PaymentsPage from './pages/admin/PaymentsPage.jsx';
import PackagesPage from './pages/admin/PackagesPage.jsx';
import UsersPage from './pages/admin/UsersPage.jsx';
import BroadcastPage from './pages/admin/BroadcastPage.jsx';
import AuditLogPage from './pages/admin/AuditLogPage.jsx';

import MemberHomePage from './pages/member/MemberHomePage.jsx';
import BuyPackagePage from './pages/member/BuyPackagePage.jsx';
import BookingPage from './pages/member/BookingPage.jsx';
import MyBookingsPage from './pages/member/MyBookingsPage.jsx';
import WorkoutPage from './pages/member/WorkoutPage.jsx';
import ProgressPage from './pages/member/ProgressPage.jsx';
import QrPage from './pages/member/QrPage.jsx';
import MyPaymentsPage from './pages/member/MyPaymentsPage.jsx';

import TrainerBookingsPage from './pages/trainer/TrainerBookingsPage.jsx';
import WeeklySchedulePage from './pages/trainer/WeeklySchedulePage.jsx';
import TrainerPlansPage from './pages/trainer/TrainerPlansPage.jsx';

/** Chỉ cho vào khi đã đăng nhập (và có 1 trong các vai trò nếu truyền roles) */
function RequireAuth({ roles, children }) {
  const { user, loading, hasRole, homePath } = useAuth();
  const location = useLocation();
  if (loading) return <div className="spinner" />;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (roles && !hasRole(...roles)) return <Navigate to={homePath()} replace />;
  return children;
}

function HomeRedirect() {
  const { user, loading, homePath } = useAuth();
  if (loading) return <div className="spinner" />;
  return <Navigate to={user ? homePath() : '/login'} replace />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />

      <Route element={<RequireAuth><Layout /></RequireAuth>}>
        <Route path="/profile" element={<ProfilePage />} />
        <Route path="/notifications" element={<NotificationsPage />} />
        {/* VNPay chuyển trình duyệt về backend, backend redirect tới đây */}
        <Route path="/payment-result" element={<PaymentResultPage />} />

        <Route path="/admin" element={<RequireAuth roles={['ADMIN', 'STAFF']}><DashboardPage /></RequireAuth>} />
        <Route path="/admin/check-in" element={<RequireAuth roles={['ADMIN', 'STAFF']}><CheckInPage /></RequireAuth>} />
        <Route path="/admin/members" element={<RequireAuth roles={['ADMIN', 'STAFF']}><MembersPage /></RequireAuth>} />
        <Route path="/admin/schedule" element={<RequireAuth roles={['ADMIN', 'STAFF']}><SchedulePage /></RequireAuth>} />
        <Route path="/admin/payments" element={<RequireAuth roles={['ADMIN', 'STAFF']}><PaymentsPage /></RequireAuth>} />
        <Route path="/admin/packages" element={<RequireAuth roles={['ADMIN']}><PackagesPage /></RequireAuth>} />
        <Route path="/admin/users" element={<RequireAuth roles={['ADMIN']}><UsersPage /></RequireAuth>} />
        <Route path="/admin/broadcast" element={<RequireAuth roles={['ADMIN']}><BroadcastPage /></RequireAuth>} />
        <Route path="/admin/audit-logs" element={<RequireAuth roles={['ADMIN']}><AuditLogPage /></RequireAuth>} />

        <Route path="/trainer" element={<RequireAuth roles={['TRAINER']}><TrainerBookingsPage /></RequireAuth>} />
        <Route path="/trainer/schedule" element={<RequireAuth roles={['TRAINER']}><WeeklySchedulePage /></RequireAuth>} />
        <Route path="/trainer/plans" element={<RequireAuth roles={['TRAINER']}><TrainerPlansPage /></RequireAuth>} />

        <Route path="/member" element={<RequireAuth roles={['MEMBER']}><MemberHomePage /></RequireAuth>} />
        <Route path="/member/packages" element={<RequireAuth roles={['MEMBER']}><BuyPackagePage /></RequireAuth>} />
        <Route path="/member/booking" element={<RequireAuth roles={['MEMBER']}><BookingPage /></RequireAuth>} />
        <Route path="/member/bookings" element={<RequireAuth roles={['MEMBER']}><MyBookingsPage /></RequireAuth>} />
        <Route path="/member/workout" element={<RequireAuth roles={['MEMBER']}><WorkoutPage /></RequireAuth>} />
        <Route path="/member/progress" element={<RequireAuth roles={['MEMBER']}><ProgressPage /></RequireAuth>} />
        <Route path="/member/qr" element={<RequireAuth roles={['MEMBER']}><QrPage /></RequireAuth>} />
        <Route path="/member/payments" element={<RequireAuth roles={['MEMBER']}><MyPaymentsPage /></RequireAuth>} />
      </Route>

      <Route path="*" element={<HomeRedirect />} />
    </Routes>
  );
}
