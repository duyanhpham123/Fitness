import { api } from '../../api/client.js';

/** Lấy link thanh toán VNPay của đơn rồi chuyển trình duyệt sang VNPay */
export async function payVnpay(paymentId) {
  const { paymentUrl } = await api(`/api/payments/${paymentId}/vnpay-url`, { method: 'POST' });
  window.location.href = paymentUrl;
}
