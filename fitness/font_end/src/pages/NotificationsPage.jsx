import { useState } from 'react';
import { CheckCheck } from 'lucide-react';
import { api } from '../api/client.js';
import { Async, Badge, PageHead, Pagination, useFetch, useToast } from '../components/ui.jsx';
import { formatDateTime, label } from '../utils/format.js';

export default function NotificationsPage() {
  const toast = useToast();
  const [page, setPage] = useState(0);
  const list = useFetch(() => api('/api/notifications/me', { query: { page, size: 20 } }), [page]);

  const markRead = async (n) => {
    if (n.read) return;
    await api(`/api/notifications/${n.id}/read`, { method: 'PUT' }).catch(toast);
    list.reload();
  };

  const markAll = async () => {
    try {
      const res = await api('/api/notifications/read-all', { method: 'PUT' });
      toast(`Đã đánh dấu ${res.count} thông báo`, 'success');
      list.reload();
    } catch (err) {
      toast(err);
    }
  };

  return (
    <>
      <PageHead title="Thông báo">
        <button className="btn secondary" onClick={markAll}><CheckCheck size={16} /> Đọc tất cả</button>
      </PageHead>
      <div className="card">
        <Async state={list} empty="Bạn chưa có thông báo nào">
          {(data) => (
            <>
              {data.content.map((n) => (
                <div
                  key={n.id}
                  className="list-item"
                  style={{ cursor: n.read ? 'default' : 'pointer', opacity: n.read ? 0.7 : 1 }}
                  onClick={() => markRead(n)}
                >
                  <Badge color={n.read ? 'gray' : 'blue'}>{label(n.type)}</Badge>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontWeight: n.read ? 400 : 600 }}>{n.title}</div>
                    <div className="muted">{n.content}</div>
                  </div>
                  <span className="muted small">{formatDateTime(n.createdAt)}</span>
                </div>
              ))}
              <Pagination page={data} onChange={setPage} />
            </>
          )}
        </Async>
      </div>
    </>
  );
}
