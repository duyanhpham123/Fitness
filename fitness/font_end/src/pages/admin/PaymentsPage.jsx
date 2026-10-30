import { useState } from 'react';
import { api } from '../../api/client.js';
import { Async, Badge, PageHead, Pagination, confirmAction, useFetch, useToast } from '../../components/ui.jsx';
import { formatDateTime, formatMoney, label } from '../../utils/format.js';

const STATUSES = ['PENDING', 'SUCCESS', 'FAILED', 'CANCELLED', ''];
// VNPay được xác nhận tự động qua IPN, chỉ thu tại quầy mới xác nhận tay
const MANUAL = ['CASH', 'BANK_TRANSFER'];

export default function PaymentsPage() {
  const toast = useToast();
  const [status, setStatus] = useState('PENDING');
  const [page, setPage] = useState(0);
  const list = useFetch(() => api('/api/payments', { query: { status, page, size: 15 } }), [status, page]);

  const confirm = async (p) => {
    if (!(await confirmAction(`Xác nhận đã nhận ${formatMoney(p.amount)} từ ${p.memberName}?`))) return;
    try {
      const res = await api(`/api/payments/${p.id}/confirm`, { method: 'POST' });
      toast(`Đã kích hoạt ${res.subscription?.packageName || 'gói tập'} cho ${p.memberName}`, 'success');
      list.reload();
    } catch (err) {
      toast(err);
    }
  };

  return (
    <>
      <PageHead title="Thanh toán" subtitle="Xác nhận các giao dịch thu tiền mặt / chuyển khoản tại quầy" />
      <div className="card">
        <div className="tabs">
          {STATUSES.map((s) => (
            <button key={s || 'all'} className={status === s ? 'active' : ''} onClick={() => { setStatus(s); setPage(0); }}>
              {s ? label(s) : 'Tất cả'}
            </button>
          ))}
        </div>
        <Async state={list} empty="Không có giao dịch">
          {(data) => (
            <>
              <div className="table-wrap">
                <table>
                  <thead><tr><th>Mã</th><th>Hội viên</th><th>Gói</th><th className="right">Số tiền</th><th>Phương thức</th><th>Tạo lúc</th><th>Trạng thái</th><th /></tr></thead>
                  <tbody>
                    {data.content.map((p) => (
                      <tr key={p.id}>
                        <td className="small">{p.code}</td>
                        <td>{p.memberName}</td>
                        <td>{p.packageName || '-'}</td>
                        <td className="right">{formatMoney(p.amount)}</td>
                        <td>{label(p.method)}</td>
                        <td>{formatDateTime(p.createdAt)}</td>
                        <td><Badge status={p.status} /></td>
                        <td className="right">
                          {p.status === 'PENDING' && MANUAL.includes(p.method) && (
                            <button className="btn success sm" onClick={() => confirm(p)}>Xác nhận đã thu</button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <Pagination page={data} onChange={setPage} />
            </>
          )}
        </Async>
      </div>
    </>
  );
}
