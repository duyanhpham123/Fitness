import { useState } from 'react';
import { LogOut, ScanLine, Search } from 'lucide-react';
import { api } from '../../api/client.js';
import { Async, Badge, PageHead, useFetch, useToast } from '../../components/ui.jsx';
import { formatTime, label } from '../../utils/format.js';

/**
 * Lễ tân check-in hội viên: quét QR (máy quét mã vạch gõ chuỗi + Enter vào ô nhập)
 * hoặc tìm hội viên để check-in thủ công; check-out người đang ở phòng tập.
 */
export default function CheckInPage() {
  const toast = useToast();
  const [openOnly, setOpenOnly] = useState(true);
  const today = useFetch(() => api('/api/check-ins/today', { query: { openOnly } }), [openOnly]);

  const done = (c) => {
    toast(`Đã check-in: ${c.memberName} (${c.packageName})`, 'success');
    today.reload();
  };

  const checkOut = async (c) => {
    try {
      await api(`/api/check-ins/${c.id}/check-out`, { method: 'POST' });
      toast(`Đã check-out: ${c.memberName}`, 'success');
      today.reload();
    } catch (err) {
      toast(err);
    }
  };

  return (
    <>
      <PageHead title="Check-in" subtitle="Quét QR trên app của hội viên hoặc check-in thủ công" />
      <div className="grid grid-2">
        <QrScan onDone={done} />
        <ManualCheckIn onDone={done} />
      </div>
      <div className="card" style={{ marginTop: 16 }}>
        <div className="card-head">
          <h2>Hôm nay</h2>
          <label className="checkbox">
            <input type="checkbox" checked={openOnly} onChange={(e) => setOpenOnly(e.target.checked)} />
            Chỉ người đang ở phòng tập
          </label>
        </div>
        <Async state={today} empty="Chưa có lượt check-in nào">
          {(list) => (
            <div className="table-wrap">
              <table>
                <thead><tr><th>Hội viên</th><th>Gói</th><th>Vào</th><th>Ra</th><th>Cách</th><th>Lễ tân</th><th /></tr></thead>
                <tbody>
                  {list.map((c) => (
                    <tr key={c.id}>
                      <td>{c.memberName}</td>
                      <td>{c.packageName}</td>
                      <td>{formatTime(c.checkedInAt)}</td>
                      <td>{c.checkedOutAt ? formatTime(c.checkedOutAt) : <Badge color="green">Đang tập</Badge>}</td>
                      <td>{label(c.method)}</td>
                      <td>{c.staffName || '-'}</td>
                      <td className="right">
                        {!c.checkedOutAt && (
                          <button className="btn secondary sm" onClick={() => checkOut(c)}><LogOut size={14} /> Check-out</button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Async>
      </div>
    </>
  );
}

function QrScan({ onDone }) {
  const toast = useToast();
  const [token, setToken] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    if (!token.trim()) return;
    setBusy(true);
    try {
      onDone(await api('/api/check-ins', { method: 'POST', body: { qrToken: token.trim() } }));
      setToken('');
    } catch (err) {
      toast(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <form className="card" onSubmit={submit}>
      <h2 style={{ marginBottom: 12 }}><ScanLine size={18} /> Quét mã QR</h2>
      <p className="muted small">Đặt con trỏ vào ô dưới rồi quét mã trên điện thoại hội viên (mã có hiệu lực 60 giây).</p>
      <div className="row">
        <input className="input" style={{ flex: 1 }} value={token} onChange={(e) => setToken(e.target.value)}
          placeholder="Mã QR" autoFocus />
        <button className="btn" disabled={busy}>Check-in</button>
      </div>
    </form>
  );
}

function ManualCheckIn({ onDone }) {
  const toast = useToast();
  const [keyword, setKeyword] = useState('');
  const [results, setResults] = useState(null);

  const search = async (e) => {
    e.preventDefault();
    try {
      setResults((await api('/api/admin/members', { query: { keyword, size: 5 } })).content);
    } catch (err) {
      toast(err);
    }
  };

  const checkIn = async (m) => {
    try {
      onDone(await api('/api/check-ins/manual', { method: 'POST', body: { memberId: m.id } }));
      setResults(null);
      setKeyword('');
    } catch (err) {
      toast(err);
    }
  };

  return (
    <div className="card">
      <h2 style={{ marginBottom: 12 }}><Search size={18} /> Check-in thủ công</h2>
      <form className="row" onSubmit={search}>
        <input className="input" style={{ flex: 1 }} value={keyword} onChange={(e) => setKeyword(e.target.value)}
          placeholder="Tên, email hoặc số điện thoại" />
        <button className="btn secondary">Tìm</button>
      </form>
      {results && results.length === 0 && <div className="empty">Không tìm thấy hội viên</div>}
      {results?.map((m) => (
        <div key={m.id} className="list-item">
          <div style={{ flex: 1 }}>
            {m.fullName} <span className="muted small">{m.phone}</span>
            <div className="small">
              {m.packageName || 'Chưa có gói'} {m.subscriptionStatus && <Badge status={m.subscriptionStatus} />}
            </div>
          </div>
          <button className="btn sm" onClick={() => checkIn(m)}>Check-in</button>
        </div>
      ))}
    </div>
  );
}
