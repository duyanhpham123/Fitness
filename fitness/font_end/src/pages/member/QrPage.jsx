import { useCallback, useEffect, useState } from 'react';
import { QRCodeSVG } from 'qrcode.react';
import { RefreshCw } from 'lucide-react';
import { api } from '../../api/client.js';
import { Async, Badge, PageHead, useFetch, useToast } from '../../components/ui.jsx';
import { formatDateTime, formatTime, label } from '../../utils/format.js';

/** Mã QR check-in: hiệu lực 60 giây, tự làm mới khi hết hạn */
export default function QrPage() {
  const toast = useToast();
  const [qr, setQr] = useState(null);
  const [left, setLeft] = useState(0);
  const history = useFetch(() => api('/api/check-ins/me', { query: { size: 10 } }), []);

  const load = useCallback(async () => {
    try {
      const res = await api('/api/check-ins/me/qr');
      setQr(res.qrToken);
      setLeft(res.expiresIn);
    } catch (err) {
      toast(err);
    }
  }, [toast]);

  useEffect(() => {
    load();
  }, [load]);

  // Đếm ngược mỗi giây
  useEffect(() => {
    if (!qr) return undefined;
    const timer = setInterval(() => setLeft((s) => Math.max(0, s - 1)), 1000);
    return () => clearInterval(timer);
  }, [qr]);

  // Hết hạn thì lấy mã mới (và cập nhật lịch sử, phòng khi mã vừa được quét)
  useEffect(() => {
    if (qr && left === 0) {
      load();
      history.reload();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [left]);

  return (
    <>
      <PageHead title="QR check-in" subtitle="Đưa mã này cho lễ tân quét khi đến phòng tập" />
      <div className="grid grid-2">
        <div className="card" style={{ textAlign: 'center' }}>
          {qr ? <QRCodeSVG value={qr} size={240} style={{ margin: '12px auto' }} /> : <div className="spinner" />}
          <p className="muted">Mã tự đổi sau <b>{left}</b> giây · mỗi mã chỉ dùng 1 lần</p>
          <button className="btn secondary" onClick={load}><RefreshCw size={16} /> Làm mới mã</button>
        </div>
        <div className="card">
          <h2 style={{ marginBottom: 12 }}>Lịch sử ra vào</h2>
          <Async state={history} empty="Chưa có lượt check-in nào">
            {(page) => page.content.map((c) => (
              <div key={c.id} className="list-item">
                <div style={{ flex: 1 }}>
                  {formatDateTime(c.checkedInAt)}
                  <div className="muted small">{c.packageName} · {label(c.method)}</div>
                </div>
                {c.checkedOutAt ? <span className="muted small">Ra {formatTime(c.checkedOutAt)}</span> : <Badge color="green">Đang tập</Badge>}
              </div>
            ))}
          </Async>
        </div>
      </div>
    </>
  );
}
