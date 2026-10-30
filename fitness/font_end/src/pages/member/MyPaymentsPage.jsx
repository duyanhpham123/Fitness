import { useState } from 'react';
import { api } from '../../api/client.js';
import { Async, Badge, PageHead, Pagination, useFetch, useToast } from '../../components/ui.jsx';
import { formatDateTime, formatMoney, label } from '../../utils/format.js';
import { payVnpay } from './payVnpay.js';

export default function MyPaymentsPage() {
  const toast = useToast();
  const [page, setPage] = useState(0);
  const list = useFetch(() => api('/api/payments/me', { query: { page, size: 15 } }), [page]);

  const pay = (p) => payVnpay(p.id).catch(toast);

  return (
    <>
      <PageHead title="Lịch sử thanh toán" />
      <div className="card">
        <Async state={list} empty="Chưa có giao dịch nào">
          {(data) => (
            <>
              <div className="table-wrap">
                <table>
                  <thead><tr><th>Mã giao dịch</th><th>Gói</th><th className="right">Số tiền</th><th>Phương thức</th><th>Thời gian</th><th>Trạng thái</th><th /></tr></thead>
                  <tbody>
                    {data.content.map((p) => (
                      <tr key={p.id}>
                        <td className="small">{p.code}</td>
                        <td>{p.packageName || '-'}</td>
                        <td className="right">{formatMoney(p.amount)}</td>
                        <td>{label(p.method)}</td>
                        <td>{formatDateTime(p.paidAt || p.createdAt)}</td>
                        <td><Badge status={p.status} /></td>
                        <td className="right">
                          {p.status === 'PENDING' && p.method === 'VNPAY' && <button className="btn sm" onClick={() => pay(p)}>Thanh toán</button>}
                          {p.status === 'PENDING' && p.method !== 'VNPAY' && <span className="muted small">Thanh toán tại quầy</span>}
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
