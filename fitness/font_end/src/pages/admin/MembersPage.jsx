import { useState } from 'react';
import { api } from '../../api/client.js';
import { Async, Avatar, Badge, PageHead, Pagination, useFetch } from '../../components/ui.jsx';
import { formatDate } from '../../utils/format.js';

export default function MembersPage() {
  const [keyword, setKeyword] = useState('');
  const [query, setQuery] = useState({ keyword: '', page: 0 });
  const list = useFetch(() => api('/api/admin/members', { query: { ...query, size: 15 } }), [query]);

  return (
    <>
      <PageHead title="Danh sách hội viên" />
      <div className="card">
        <form className="row" style={{ marginBottom: 14 }}
          onSubmit={(e) => { e.preventDefault(); setQuery({ keyword, page: 0 }); }}>
          <input className="input" style={{ maxWidth: 360 }} value={keyword} onChange={(e) => setKeyword(e.target.value)}
            placeholder="Tìm theo tên, email, số điện thoại..." />
          <button className="btn secondary">Tìm</button>
        </form>
        <Async state={list} empty="Không có hội viên nào">
          {(page) => (
            <>
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr><th>Hội viên</th><th>SĐT</th><th>Gói tập</th><th>Hết hạn</th><th>Trạng thái gói</th><th>Tài khoản</th></tr>
                  </thead>
                  <tbody>
                    {page.content.map((m) => (
                      <tr key={m.id}>
                        <td>
                          <div className="row">
                            <Avatar name={m.fullName} src={m.avatarUrl} />
                            <div>{m.fullName}<div className="muted small">{m.email}</div></div>
                          </div>
                        </td>
                        <td>{m.phone || '-'}</td>
                        <td>{m.packageName || <span className="muted">Chưa mua</span>}</td>
                        <td>{formatDate(m.subscriptionEndDate)}</td>
                        <td>{m.subscriptionStatus ? <Badge status={m.subscriptionStatus} /> : '-'}</td>
                        <td><Badge status={m.status} /></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <Pagination page={page} onChange={(p) => setQuery({ ...query, page: p })} />
            </>
          )}
        </Async>
      </div>
    </>
  );
}
