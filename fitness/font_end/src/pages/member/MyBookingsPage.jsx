import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api/client.js';
import { Async, Badge, PageHead, Pagination, useFetch, useToast } from '../../components/ui.jsx';
import { formatDateTime, formatTime, label } from '../../utils/format.js';

// Backend chỉ cho hủy trước giờ tập ít nhất 2 giờ
const CANCEL_BEFORE_MS = 2 * 60 * 60 * 1000;

export default function MyBookingsPage() {
  const toast = useToast();
  const [page, setPage] = useState(0);
  const list = useFetch(() => api('/api/bookings/me', { query: { page, size: 15 } }), [page]);

  const cancel = async (b) => {
    const reason = window.prompt('Lý do hủy (không bắt buộc):', '');
    if (reason === null) return;
    try {
      await api(`/api/bookings/${b.id}`, { method: 'DELETE', query: { reason } });
      toast(b.type === 'PT' ? 'Đã hủy, buổi PT được hoàn lại vào gói' : 'Đã hủy lịch', 'success');
      list.reload();
    } catch (err) {
      toast(err);
    }
  };

  return (
    <>
      <PageHead title="Lịch của tôi">
        <Link className="btn" to="/member/booking">Đặt lịch mới</Link>
      </PageHead>
      <div className="card">
        <Async state={list} empty="Bạn chưa đặt lịch nào">
          {(data) => (
            <>
              <div className="table-wrap">
                <table>
                  <thead><tr><th>Thời gian</th><th>Loại</th><th>Chi tiết</th><th>Trạng thái</th><th /></tr></thead>
                  <tbody>
                    {data.content.map((b) => {
                      const cancellable = b.status === 'CONFIRMED' && new Date(b.startAt) - new Date() > CANCEL_BEFORE_MS;
                      return (
                        <tr key={b.id}>
                          <td>{formatDateTime(b.startAt)} - {formatTime(b.endAt)}</td>
                          <td>{label(b.type)}</td>
                          <td>
                            {b.type === 'PT' ? `HLV ${b.trainerName}` : `${b.className}${b.room ? ` · ${b.room}` : ''}`}
                            {b.cancelReason && <div className="muted small">{b.cancelReason}</div>}
                          </td>
                          <td><Badge status={b.status} /></td>
                          <td className="right">
                            {cancellable && <button className="btn secondary sm" onClick={() => cancel(b)}>Hủy lịch</button>}
                          </td>
                        </tr>
                      );
                    })}
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
