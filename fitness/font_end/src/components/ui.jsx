import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { X } from 'lucide-react';
import { label, tone } from '../utils/format.js';

// ---------- Toast ----------
const ToastContext = createContext(() => {});

export function ToastProvider({ children }) {
  const [items, setItems] = useState([]);
  const show = useCallback((message, type = 'info') => {
    const id = Math.random();
    setItems((list) => [...list, { id, message, type }]);
    setTimeout(() => setItems((list) => list.filter((t) => t.id !== id)), 4000);
  }, []);
  return (
    <ToastContext.Provider value={show}>
      {children}
      <div className="toasts">
        {items.map((t) => (
          <div key={t.id} className={`toast ${t.type}`}>{t.message}</div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

/** toast('Đã lưu', 'success') / toast(error) — error lấy message từ ApiError */
export function useToast() {
  const show = useContext(ToastContext);
  return useCallback(
    (msg, type) => (msg instanceof Error ? show(msg.message, 'error') : show(msg, type)),
    [show],
  );
}

// ---------- Tải dữ liệu ----------
/**
 * const { data, loading, error, reload } = useFetch(() => api('/api/x'), [dep]);
 */
export function useFetch(loader, deps = []) {
  const [state, setState] = useState({ data: null, loading: true, error: null });
  const [version, setVersion] = useState(0);
  useEffect(() => {
    let alive = true;
    setState((s) => ({ ...s, loading: true, error: null }));
    loader()
      .then((data) => alive && setState({ data, loading: false, error: null }))
      .catch((error) => alive && setState({ data: null, loading: false, error }));
    return () => {
      alive = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, version]);
  return { ...state, reload: () => setVersion((v) => v + 1) };
}

/** Bọc phần hiển thị dữ liệu: đang tải / lỗi / rỗng */
export function Async({ state, empty = 'Chưa có dữ liệu', isEmpty, children }) {
  if (state.loading && !state.data) return <div className="spinner" />;
  if (state.error) return <div className="alert error">{state.error.message}</div>;
  const data = state.data;
  const blank = isEmpty ? isEmpty(data) : !data || (Array.isArray(data) && data.length === 0)
    || (data.content && data.content.length === 0);
  if (blank) return <div className="empty">{empty}</div>;
  return children(data);
}

// ---------- Form ----------
export function Field({ label: text, error, children, hint }) {
  return (
    <div className="field">
      {text && <label>{text}</label>}
      {children}
      {hint && !error && <span className="muted small">{hint}</span>}
      {error && <span className="error">{error}</span>}
    </div>
  );
}

/**
 * Quản lý giá trị form + lỗi từng field trả về từ backend (ApiError.errors).
 * const f = useForm({ name: '' }); <input {...f.bind('name')} />
 */
export function useForm(initial) {
  const [values, setValues] = useState(initial);
  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const set = (name, value) => setValues((v) => ({ ...v, [name]: value }));
  return {
    values,
    errors,
    submitting,
    set,
    setValues,
    bind: (name, type = 'text') => ({
      name,
      className: `input${errors[name] ? ' invalid' : ''}`,
      ...(type === 'checkbox'
        ? { type, checked: !!values[name], onChange: (e) => set(name, e.target.checked) }
        : { type, value: values[name] ?? '', onChange: (e) => set(name, e.target.value) }),
    }),
    /** Chạy hàm gửi; lỗi validation của backend được gắn vào từng field, lỗi khác ném tiếp */
    async submit(fn) {
      setSubmitting(true);
      setErrors({});
      try {
        return await fn(values);
      } catch (e) {
        if (e.errors && Object.keys(e.errors).length) setErrors(e.errors);
        throw e;
      } finally {
        setSubmitting(false);
      }
    },
  };
}

// ---------- Hiển thị ----------
export function Badge({ status, children, color }) {
  return <span className={`badge ${color || tone(status)}`}>{children ?? label(status)}</span>;
}

export function Modal({ title, onClose, children, footer, wide }) {
  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className={`modal${wide ? ' wide' : ''}`}>
        <div className="modal-head">
          <h2>{title}</h2>
          <button className="btn ghost" onClick={onClose} aria-label="Đóng"><X size={18} /></button>
        </div>
        <div className="modal-body">{children}</div>
        {footer && <div className="modal-foot">{footer}</div>}
      </div>
    </div>
  );
}

export function Pagination({ page, onChange }) {
  if (!page || page.totalPages <= 1) return null;
  return (
    <div className="pagination">
      <span className="muted small">Trang {page.page + 1}/{page.totalPages} · {page.totalElements} dòng</span>
      <button className="btn secondary sm" disabled={page.page === 0} onClick={() => onChange(page.page - 1)}>Trước</button>
      <button className="btn secondary sm" disabled={page.page + 1 >= page.totalPages} onClick={() => onChange(page.page + 1)}>Sau</button>
    </div>
  );
}

export function StatCard({ icon: Icon, color, title, value, change }) {
  return (
    <div className="card stat">
      <div className="stat-icon" style={{ background: `${color}1a`, color }}><Icon size={22} /></div>
      <div>
        <div className="muted small">{title}</div>
        <div className="stat-value">{value}</div>
        {change !== undefined && change !== null && (
          <div className={change >= 0 ? 'up' : 'down'}>
            {change >= 0 ? '↑' : '↓'} {Math.abs(change)}% <span className="muted">so với tháng trước</span>
          </div>
        )}
      </div>
    </div>
  );
}

export function Avatar({ name, src }) {
  return (
    <div className="avatar">
      {src ? <img src={src} alt="" /> : (name || '?').trim().split(' ').pop().charAt(0).toUpperCase()}
    </div>
  );
}

export function PageHead({ title, subtitle, children }) {
  return (
    <div className="page-head">
      <div>
        <h1>{title}</h1>
        {subtitle && <p>{subtitle}</p>}
      </div>
      <div className="row">{children}</div>
    </div>
  );
}

/** Hộp xác nhận nhanh: await confirmAction('Hủy lịch?') */
export const confirmAction = (message) => Promise.resolve(window.confirm(message));
