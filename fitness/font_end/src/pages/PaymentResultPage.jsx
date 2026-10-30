import { Link, useSearchParams } from 'react-router-dom';
import { CheckCircle2, XCircle } from 'lucide-react';

const MESSAGES = {
  SUCCESS: 'Thanh toán thành công, gói tập đã được kích hoạt.',
  FAILED: 'Giao dịch không thành công hoặc đã bị hủy. Bạn có thể mua lại gói.',
  CANCELLED: 'Giao dịch đã hết hạn.',
  PENDING: 'Giao dịch đang chờ xác nhận từ VNPay, vui lòng kiểm tra lại sau ít phút.',
  INVALID: 'Dữ liệu trả về không hợp lệ (sai chữ ký).',
  NOT_FOUND: 'Không tìm thấy giao dịch.',
  ERROR: 'Có lỗi khi xử lý kết quả thanh toán.',
};

/** Trang VNPay đưa người dùng về sau khi thanh toán (?paymentCode=...&result=...) */
export default function PaymentResultPage() {
  const [params] = useSearchParams();
  const result = params.get('result') || 'ERROR';
  const ok = result === 'SUCCESS';
  return (
    <div className="card" style={{ maxWidth: 520, margin: '40px auto', textAlign: 'center' }}>
      {ok ? <CheckCircle2 size={56} color="var(--green)" /> : <XCircle size={56} color="var(--red)" />}
      <h1 style={{ margin: '12px 0' }}>{ok ? 'Thanh toán thành công' : 'Thanh toán chưa thành công'}</h1>
      <p className="muted">{MESSAGES[result] || MESSAGES.ERROR}</p>
      {params.get('paymentCode') && <p className="small muted">Mã giao dịch: {params.get('paymentCode')}</p>}
      <div className="row" style={{ justifyContent: 'center', marginTop: 16 }}>
        <Link className="btn" to="/member/packages">Gói tập của tôi</Link>
        <Link className="btn secondary" to="/member/payments">Lịch sử thanh toán</Link>
      </div>
    </div>
  );
}
