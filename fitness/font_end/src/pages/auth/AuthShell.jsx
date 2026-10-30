import { Dumbbell } from 'lucide-react';

/** Khung chung cho các trang đăng nhập / đăng ký: ảnh bên trái, form bên phải */
export default function AuthShell({ title, children }) {
  return (
    <div className="auth-page">
      <div className="auth-hero">
        <Dumbbell size={56} />
        <h1>FITNESS</h1>
        <p>Stronger Every Day</p>
        <p>Đặt lịch tập, theo dõi gói tập và tiến độ của bạn ở một nơi.</p>
      </div>
      <div className="auth-form">
        <div className="card">
          <h1 style={{ marginBottom: 20 }}>{title}</h1>
          {children}
        </div>
      </div>
    </div>
  );
}
