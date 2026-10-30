import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { api } from '../../api/client.js';
import { Async, Field, PageHead, useFetch, useForm, useToast } from '../../components/ui.jsx';
import { formatDate, toDateInput } from '../../utils/format.js';

export default function ProgressPage() {
  const toast = useToast();
  const metrics = useFetch(() => api('/api/body-metrics/me'), []);
  const form = useForm({ measuredAt: toDateInput(), weightKg: '', heightCm: '', bodyFatPct: '', muscleMassKg: '', note: '' });

  const save = async (e) => {
    e.preventDefault();
    const num = (v) => (v === '' ? null : Number(v));
    try {
      await form.submit((v) => api('/api/body-metrics/me', {
        method: 'PUT',
        body: {
          measuredAt: v.measuredAt, weightKg: num(v.weightKg), heightCm: num(v.heightCm),
          bodyFatPct: num(v.bodyFatPct), muscleMassKg: num(v.muscleMassKg), note: v.note || null,
        },
      }));
      toast('Đã lưu chỉ số', 'success');
      metrics.reload();
    } catch (err) {
      toast(err);
    }
  };

  return (
    <>
      <PageHead title="Chỉ số cơ thể" subtitle="Theo dõi cân nặng, BMI và tỷ lệ mỡ theo thời gian" />
      <div className="grid grid-2">
        <div className="card">
          <h2 style={{ marginBottom: 12 }}>Biểu đồ tiến độ</h2>
          <Async state={metrics} empty="Chưa có số liệu, hãy ghi chỉ số đầu tiên">
            {(list) => (
              <ResponsiveContainer width="100%" height={280}>
                <LineChart data={list.map((m) => ({
                  day: formatDate(m.measuredAt).slice(0, 5),
                  'Cân nặng (kg)': m.weightKg && Number(m.weightKg),
                  BMI: m.bmi && Number(m.bmi),
                  '% mỡ': m.bodyFatPct && Number(m.bodyFatPct),
                }))}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#eef0f4" />
                  <XAxis dataKey="day" fontSize={12} />
                  <YAxis fontSize={12} />
                  <Tooltip />
                  <Legend />
                  <Line type="monotone" dataKey="Cân nặng (kg)" stroke="#2563eb" connectNulls />
                  <Line type="monotone" dataKey="BMI" stroke="#16a34a" connectNulls />
                  <Line type="monotone" dataKey="% mỡ" stroke="#ea580c" connectNulls />
                </LineChart>
              </ResponsiveContainer>
            )}
          </Async>
        </div>
        <form className="card" onSubmit={save}>
          <h2 style={{ marginBottom: 12 }}>Ghi chỉ số</h2>
          <Field label="Ngày đo" error={form.errors.measuredAt}><input {...form.bind('measuredAt', 'date')} max={toDateInput()} /></Field>
          <div className="grid grid-2">
            <Field label="Cân nặng (kg)" error={form.errors.weightKg}><input {...form.bind('weightKg', 'number')} step="0.1" /></Field>
            <Field label="Chiều cao (cm)" error={form.errors.heightCm}><input {...form.bind('heightCm', 'number')} step="0.1" /></Field>
            <Field label="Tỷ lệ mỡ (%)" error={form.errors.bodyFatPct}><input {...form.bind('bodyFatPct', 'number')} step="0.1" /></Field>
            <Field label="Khối lượng cơ (kg)" error={form.errors.muscleMassKg}><input {...form.bind('muscleMassKg', 'number')} step="0.1" /></Field>
          </div>
          <Field label="Ghi chú" error={form.errors.note}><input {...form.bind('note')} /></Field>
          <button className="btn" disabled={form.submitting}>Lưu</button>
          <p className="muted small">Ghi lại trong cùng 1 ngày sẽ cập nhật số liệu của ngày đó.</p>
        </form>
      </div>
      <div className="card">
        <Async state={metrics} empty="">
          {(list) => (
            <div className="table-wrap">
              <table>
                <thead><tr><th>Ngày</th><th>Cân nặng</th><th>Chiều cao</th><th>BMI</th><th>% mỡ</th><th>Cơ</th><th>Ghi chú</th></tr></thead>
                <tbody>
                  {[...list].reverse().map((m) => (
                    <tr key={m.id}>
                      <td>{formatDate(m.measuredAt)}</td>
                      <td>{m.weightKg ?? '-'}</td>
                      <td>{m.heightCm ?? '-'}</td>
                      <td>{m.bmi ?? '-'}</td>
                      <td>{m.bodyFatPct ?? '-'}</td>
                      <td>{m.muscleMassKg ?? '-'}</td>
                      <td className="muted">{m.note || ''}</td>
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
