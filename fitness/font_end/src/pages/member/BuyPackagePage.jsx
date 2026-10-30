import { useState } from 'react';
import { CheckCircle2 } from 'lucide-react';
import { api, newIdempotencyKey } from '../../api/client.js';
import { Async, Badge, Field, Modal, PageHead, useFetch, useToast } from '../../components/ui.jsx';
import { formatDate, formatMoney, label, toDateInput } from '../../utils/format.js';
import { payVnpay } from './payVnpay.js';

const METHODS = ['VNPAY', 'CASH', 'BANK_TRANSFER'];

export default function BuyPackagePage() {
  const packages = useFetch(() => api('/api/packages'), []);
  const subs = useFetch(() => api('/api/subscriptions/me'), []);
  const [buying, setBuying] = useState(null);
  const [freezing, setFreezing] = useState(null);
  const toast = useToast();

  const unfreeze = async (s) => {
    try {
      await api(`/api/subscriptions/${s.id}/unfreeze`, { method: 'POST' });
      toast('Đã hủy bảo lưu', 'success');
      subs.reload();
    } catch (err) {
      toast(err);
    }
  };

  const today = toDateInput();
  return (
    <>
      <PageHead title="Gói tập" subtitle="Mua mới hoặc gia hạn; gói mới tự nối tiếp sau gói đang dùng" />
      <Async state={packages}>
        {(list) => (
          <div className="grid grid-3">
            {list.map((p) => (
              <div key={p.id} className="card package-card">
                <h2>{p.name}</h2>
                <div className="price">{formatMoney(p.price)}</div>
                <div className="muted">{p.description}</div>
                <div className="small"><CheckCircle2 size={14} color="var(--green)" /> {p.durationDays} ngày tập không giới hạn</div>
                {p.ptSessions > 0 && <div className="small"><CheckCircle2 size={14} color="var(--green)" /> {p.ptSessions} buổi PT 1-1</div>}
                {p.maxFreezeDays > 0 && <div className="small"><CheckCircle2 size={14} color="var(--green)" /> Bảo lưu tối đa {p.maxFreezeDays} ngày</div>}
                <span className="spacer" />
                <button className="btn block" onClick={() => setBuying(p)}>Đăng ký</button>
              </div>
            ))}
          </div>
        )}
      </Async>

      <div className="card" style={{ marginTop: 16 }}>
        <h2 style={{ marginBottom: 12 }}>Gói đã mua</h2>
        <Async state={subs} empty="Bạn chưa mua gói nào">
          {(list) => (
            <div className="table-wrap">
              <table>
                <thead><tr><th>Gói</th><th>Bắt đầu</th><th>Kết thúc</th><th>Buổi PT</th><th>Bảo lưu còn</th><th>Trạng thái</th><th /></tr></thead>
                <tbody>
                  {list.map((s) => (
                    <tr key={s.id}>
                      <td>{s.packageName}</td>
                      <td>{formatDate(s.startDate)}</td>
                      <td>{formatDate(s.endDate)}</td>
                      <td>{s.ptSessionsRemaining}</td>
                      <td>{s.remainingFreezeDays} ngày</td>
                      <td>
                        <Badge status={s.status} />
                        {s.status === 'FROZEN' && <div className="muted small">đến {formatDate(s.frozenUntil)}</div>}
                      </td>
                      <td className="right">
                        {s.status === 'ACTIVE' && s.startDate <= today && s.remainingFreezeDays > 0 && (
                          <button className="btn secondary sm" onClick={() => setFreezing(s)}>Bảo lưu</button>
                        )}
                        {s.status === 'FROZEN' && <button className="btn secondary sm" onClick={() => unfreeze(s)}>Hủy bảo lưu</button>}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Async>
      </div>

      {buying && <BuyModal pkg={buying} onClose={() => setBuying(null)} onDone={subs.reload} />}
      {freezing && <FreezeModal sub={freezing} onClose={() => setFreezing(null)} onDone={subs.reload} />}
    </>
  );
}

function BuyModal({ pkg, onClose, onDone }) {
  const toast = useToast();
  const [method, setMethod] = useState('VNPAY');
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState(null);
  // 1 khóa cho 1 lần mở hộp thoại: bấm "Thanh toán" 2 lần cũng chỉ tạo 1 đơn
  const [key] = useState(newIdempotencyKey);

  const buy = async () => {
    setBusy(true);
    try {
      const res = await api('/api/subscriptions', {
        method: 'POST', body: { packageId: pkg.id, method }, headers: { 'Idempotency-Key': key },
      });
      onDone();
      if (!res.payment) {
        toast('Gói đã được kích hoạt', 'success');
        onClose();
      } else if (method === 'VNPAY') {
        await payVnpay(res.payment.id);
      } else {
        setResult(res.payment);
      }
    } catch (err) {
      toast(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal title={`Đăng ký ${pkg.name}`} onClose={onClose}
      footer={result
        ? <button className="btn" onClick={onClose}>Đóng</button>
        : <><button className="btn secondary" onClick={onClose}>Hủy</button><button className="btn" disabled={busy} onClick={buy}>Thanh toán {formatMoney(pkg.price)}</button></>}>
      {result ? (
        <div className="alert success">
          Đã tạo đơn <b>{result.code}</b>. Vui lòng thanh toán <b>{formatMoney(result.amount)}</b> ({label(result.method)}) tại quầy lễ tân;
          gói sẽ được kích hoạt ngay khi lễ tân xác nhận.
        </div>
      ) : (
        <Field label="Phương thức thanh toán">
          {METHODS.map((m) => (
            <label key={m} className="checkbox" style={{ marginBottom: 8 }}>
              <input type="radio" name="method" checked={method === m} onChange={() => setMethod(m)} />
              {label(m)} {m === 'VNPAY' ? '(thẻ ATM / QR ngân hàng)' : '(tại quầy)'}
            </label>
          ))}
        </Field>
      )}
    </Modal>
  );
}

function FreezeModal({ sub, onClose, onDone }) {
  const toast = useToast();
  const [days, setDays] = useState(Math.min(7, sub.remainingFreezeDays));
  const save = async () => {
    try {
      await api(`/api/subscriptions/${sub.id}/freeze`, { method: 'POST', body: { days: Number(days) } });
      toast('Đã bảo lưu gói tập', 'success');
      onDone();
      onClose();
    } catch (err) {
      toast(err);
    }
  };
  return (
    <Modal title={`Bảo lưu ${sub.packageName}`} onClose={onClose}
      footer={<><button className="btn secondary" onClick={onClose}>Đóng</button><button className="btn" onClick={save}>Bảo lưu</button></>}>
      <Field label="Số ngày bảo lưu (tính từ hôm nay)" hint={`Còn được bảo lưu ${sub.remainingFreezeDays} ngày; ngày kết thúc gói lùi tương ứng`}>
        <input className="input" type="number" min={1} max={sub.remainingFreezeDays} value={days} onChange={(e) => setDays(e.target.value)} />
      </Field>
    </Modal>
  );
}
