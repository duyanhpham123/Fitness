// Backend trả LocalDateTime dạng "2026-10-30T08:00:00" (không có múi giờ) = giờ Việt Nam,
// new Date() hiểu chuỗi không có múi giờ là giờ máy người dùng, nên hiển thị đúng ở Việt Nam.

const money = new Intl.NumberFormat('vi-VN');

export const formatMoney = (v) => (v === null || v === undefined ? '-' : `${money.format(Number(v))}đ`);

export function formatDate(v) {
  if (!v) return '-';
  const d = new Date(v.length === 10 ? `${v}T00:00:00` : v);
  return d.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' });
}

export function formatDateTime(v) {
  if (!v) return '-';
  return new Date(v).toLocaleString('vi-VN', {
    hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit', year: 'numeric',
  });
}

export function formatTime(v) {
  if (!v) return '-';
  return new Date(v).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
}

// yyyy-MM-dd theo giờ máy (không dùng toISOString vì nó đổi sang UTC, lệch ngày lúc 0-7h sáng)
export function toDateInput(d = new Date()) {
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export function addDays(dateStr, days) {
  const d = new Date(`${dateStr}T00:00:00`);
  d.setDate(d.getDate() + days);
  return toDateInput(d);
}

export const WEEKDAYS = ['', 'Thứ 2', 'Thứ 3', 'Thứ 4', 'Thứ 5', 'Thứ 6', 'Thứ 7', 'Chủ nhật'];

export const LABELS = {
  // Trạng thái chung
  ACTIVE: 'Hoạt động', PENDING: 'Chờ xử lý', LOCKED: 'Bị khóa', EXPIRED: 'Hết hạn', FROZEN: 'Bảo lưu',
  CANCELLED: 'Đã hủy', SUCCESS: 'Thành công', FAILED: 'Thất bại', REFUNDED: 'Đã hoàn tiền',
  CONFIRMED: 'Đã xác nhận', COMPLETED: 'Hoàn thành', NO_SHOW: 'Vắng mặt', SCHEDULED: 'Sắp diễn ra',
  ARCHIVED: 'Lưu trữ',
  // Vai trò
  ADMIN: 'Quản trị', STAFF: 'Lễ tân', TRAINER: 'Huấn luyện viên', MEMBER: 'Hội viên',
  // Phương thức thanh toán
  VNPAY: 'VNPay', MOMO: 'MoMo', ZALOPAY: 'ZaloPay', CASH: 'Tiền mặt', BANK_TRANSFER: 'Chuyển khoản',
  // Loại lịch / check-in
  PT: 'PT 1-1', CLASS: 'Lớp học', QR: 'QR', MANUAL: 'Thủ công',
  // Nhóm cơ
  CHEST: 'Ngực', BACK: 'Lưng', SHOULDERS: 'Vai', ARMS: 'Tay', LEGS: 'Chân', CORE: 'Bụng', FULL_BODY: 'Toàn thân',
  CARDIO: 'Cardio',
  // Thông báo
  BOOKING: 'Lịch tập', PAYMENT: 'Thanh toán', MEMBERSHIP: 'Gói tập', PROMOTION: 'Khuyến mãi', SYSTEM: 'Hệ thống',
  MALE: 'Nam', FEMALE: 'Nữ', OTHER: 'Khác',
};

export const label = (v) => LABELS[v] || v || '-';

// Màu badge theo trạng thái
export function tone(status) {
  if (['ACTIVE', 'SUCCESS', 'CONFIRMED', 'COMPLETED', 'SCHEDULED'].includes(status)) return 'green';
  if (['PENDING', 'FROZEN'].includes(status)) return 'amber';
  if (['LOCKED', 'FAILED', 'CANCELLED', 'NO_SHOW', 'EXPIRED'].includes(status)) return 'red';
  return 'gray';
}
