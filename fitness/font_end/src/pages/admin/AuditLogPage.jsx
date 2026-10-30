import { useState } from 'react';
import { api } from '../../api/client.js';
import { Async, Badge, PageHead, Pagination, useFetch } from '../../components/ui.jsx';
import { formatDateTime } from '../../utils/format.js';

const ACTIONS = [
  'LOGIN', 'REGISTER', 'PASSWORD_CHANGE', 'PASSWORD_RESET', 'SUBSCRIPTION_PURCHASE', 'PAYMENT_CONFIRM', 'PAYMENT_SUCCESS',
  'PAYMENT_FAILED', 'BOOKING_CREATE', 'BOOKING_CANCEL', 'BOOKING_ATTENDANCE', 'CHECK_IN', 'CHECK_OUT', 'CLASS_SESSION_CANCEL',
  'USER_CREATE', 'USER_STATUS_CHANGE', 'USER_ROLES_CHANGE', 'PACKAGE_CREATE', 'PACKAGE_UPDATE', 'NOTIFICATION_BROADCAST',
];

export default function AuditLogPage() {
  const [filter, setFilter] = useState({ action: '', entityType: '', from: '', to: '', page: 0 });
  const list = useFetch(() => api('/api/audit-logs', {
    query: {
      ...filter,
      from: filter.from ? `${filter.from}T00:00:00` : '',
      to: filter.to ? `${filter.to}T23:59:59` : '',
      size: 20,
    },
  }), [filter]);
  const set = (k, v) => setFilter({ ...filter, [k]: v, page: 0 });

  return (
    <>
      <PageHead title="Nhật ký thao tác" />
      <div className="card">
        <div className="row" style={{ marginBottom: 14 }}>
          <select className="input" style={{ width: 230 }} value={filter.action} onChange={(e) => set('action', e.target.value)}>
            <option value="">Mọi hành động</option>
            {ACTIONS.map((a) => <option key={a} value={a}>{a}</option>)}
          </select>
          <input className="input" style={{ width: 160 }} value={filter.entityType} onChange={(e) => set('entityType', e.target.value)} placeholder="Đối tượng (PAYMENT...)" />
          <span className="muted">Từ</span><input type="date" className="input" style={{ width: 160 }} value={filter.from} onChange={(e) => set('from', e.target.value)} />
          <span className="muted">đến</span><input type="date" className="input" style={{ width: 160 }} value={filter.to} onChange={(e) => set('to', e.target.value)} />
        </div>
        <Async state={list} empty="Không có log">
          {(page) => (
            <>
              <div className="table-wrap">
                <table>
                  <thead><tr><th>Thời gian</th><th>Người thực hiện</th><th>Hành động</th><th>Đối tượng</th><th>Chi tiết</th><th>IP</th></tr></thead>
                  <tbody>
                    {page.content.map((l) => (
                      <tr key={l.id}>
                        <td>{formatDateTime(l.createdAt)}</td>
                        <td>{l.actorEmail || <span className="muted">Hệ thống</span>}</td>
                        <td><Badge color="blue">{l.action}</Badge></td>
                        <td>{l.entityType ? `${l.entityType} #${l.entityId ?? ''}` : '-'}</td>
                        <td>{l.newValue ? <pre className="json">{JSON.stringify(l.newValue, null, 1)}</pre> : '-'}</td>
                        <td className="small">{l.ipAddress || '-'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <Pagination page={page} onChange={(p) => setFilter({ ...filter, page: p })} />
            </>
          )}
        </Async>
      </div>
    </>
  );
}
