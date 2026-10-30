import { useState } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import { api } from '../api/client.js';
import { label } from '../utils/format.js';
import { Field, Modal, useFetch, useToast } from './ui.jsx';

const emptyItem = () => ({ exerciseId: '', sets: 3, reps: 10, durationSeconds: '', restSeconds: 60, weightKg: '', note: '' });
const emptyDay = (n) => ({ dayNumber: n, title: '', exercises: [emptyItem()] });

/**
 * Soạn / sửa kế hoạch tập (dùng chung cho HLV và hội viên tự soạn).
 * @param plan kế hoạch có sẵn khi sửa, null khi tạo mới
 * @param memberPicker HLV truyền vào ô chọn hội viên; hội viên tự soạn thì bỏ trống
 */
export default function PlanEditor({ plan, memberId, memberPicker, onClose, onSaved }) {
  const toast = useToast();
  const exercises = useFetch(() => api('/api/exercises'), []);
  const [name, setName] = useState(plan?.name || '');
  const [goal, setGoal] = useState(plan?.goal || '');
  const [days, setDays] = useState(plan
    ? plan.days.map((d) => ({
      dayNumber: d.dayNumber,
      title: d.title,
      exercises: d.exercises.map((e) => ({
        exerciseId: e.exerciseId, sets: e.sets ?? '', reps: e.reps ?? '', durationSeconds: e.durationSeconds ?? '',
        restSeconds: e.restSeconds ?? '', weightKg: e.weightKg ?? '', note: e.note ?? '',
      })),
    }))
    : [emptyDay(1)]);
  const [busy, setBusy] = useState(false);

  const updateDay = (i, patch) => setDays(days.map((d, idx) => (idx === i ? { ...d, ...patch } : d)));
  const updateItem = (di, ii, patch) => updateDay(di, {
    exercises: days[di].exercises.map((e, idx) => (idx === ii ? { ...e, ...patch } : e)),
  });

  const num = (v) => (v === '' || v === null ? null : Number(v));
  const save = async () => {
    setBusy(true);
    try {
      const body = {
        memberId: memberId || null,
        name,
        goal: goal || null,
        days: days.map((d) => ({
          dayNumber: Number(d.dayNumber),
          title: d.title,
          exercises: d.exercises.map((e) => ({
            exerciseId: num(e.exerciseId), sets: num(e.sets), reps: num(e.reps), durationSeconds: num(e.durationSeconds),
            restSeconds: num(e.restSeconds), weightKg: num(e.weightKg), note: e.note || null,
          })),
        })),
      };
      await api(plan ? `/api/workout-plans/${plan.id}` : '/api/workout-plans', { method: plan ? 'PUT' : 'POST', body });
      toast('Đã lưu kế hoạch tập', 'success');
      onSaved();
      onClose();
    } catch (err) {
      // Lỗi validation lồng nhau (days[0].exercises...) gộp vào 1 thông báo cho dễ đọc
      const fieldErrors = Object.values(err.errors || {});
      toast(fieldErrors.length ? new Error(fieldErrors[0]) : err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal wide title={plan ? 'Sửa kế hoạch tập' : 'Soạn kế hoạch tập'} onClose={onClose}
      footer={<><button className="btn secondary" onClick={onClose}>Đóng</button><button className="btn" disabled={busy} onClick={save}>Lưu kế hoạch</button></>}>
      {memberPicker}
      <div className="grid grid-2">
        <Field label="Tên kế hoạch"><input className="input" value={name} onChange={(e) => setName(e.target.value)} placeholder="Tuần 1 - Nhóm cơ bản" /></Field>
        <Field label="Mục tiêu"><input className="input" value={goal} onChange={(e) => setGoal(e.target.value)} placeholder="Tăng cơ, giảm mỡ..." /></Field>
      </div>

      {days.map((day, di) => (
        <div key={di} className="card" style={{ background: '#fafbfd', marginTop: 12 }}>
          <div className="row" style={{ marginBottom: 10 }}>
            <b>Ngày</b>
            <input className="input" type="number" min={1} style={{ width: 70 }} value={day.dayNumber}
              onChange={(e) => updateDay(di, { dayNumber: e.target.value })} />
            <input className="input" style={{ flex: 1 }} placeholder="Ngực + Tay sau" value={day.title}
              onChange={(e) => updateDay(di, { title: e.target.value })} />
            {days.length > 1 && (
              <button className="btn ghost" onClick={() => setDays(days.filter((_, i) => i !== di))} aria-label="Xóa ngày"><Trash2 size={16} /></button>
            )}
          </div>
          <div className="table-wrap">
            <table>
              <thead><tr><th>Bài tập</th><th>Hiệp</th><th>Lần</th><th>Giây</th><th>Nghỉ (s)</th><th>Tạ (kg)</th><th /></tr></thead>
              <tbody>
                {day.exercises.map((item, ii) => (
                  <tr key={ii}>
                    <td style={{ minWidth: 200 }}>
                      <select className="input" value={item.exerciseId} onChange={(e) => updateItem(di, ii, { exerciseId: e.target.value })}>
                        <option value="">-- Chọn --</option>
                        {exercises.data?.map((ex) => <option key={ex.id} value={ex.id}>{ex.name} ({label(ex.muscleGroup)})</option>)}
                      </select>
                    </td>
                    {['sets', 'reps', 'durationSeconds', 'restSeconds', 'weightKg'].map((k) => (
                      <td key={k}><input className="input" type="number" min={0} style={{ width: 80 }} value={item[k]}
                        onChange={(e) => updateItem(di, ii, { [k]: e.target.value })} /></td>
                    ))}
                    <td>
                      {day.exercises.length > 1 && (
                        <button className="btn ghost" aria-label="Xóa bài"
                          onClick={() => updateDay(di, { exercises: day.exercises.filter((_, i) => i !== ii) })}><Trash2 size={14} /></button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <button className="btn ghost sm" onClick={() => updateDay(di, { exercises: [...day.exercises, emptyItem()] })}>
            <Plus size={14} /> Thêm bài tập
          </button>
        </div>
      ))}
      <button className="btn secondary sm" style={{ marginTop: 12 }}
        onClick={() => setDays([...days, emptyDay(Math.max(0, ...days.map((d) => Number(d.dayNumber) || 0)) + 1)])}>
        <Plus size={14} /> Thêm ngày tập
      </button>
      <p className="muted small">Mỗi bài cần số lần (bài tạ) hoặc số giây (plank, chạy bộ).</p>
    </Modal>
  );
}

/** Hiển thị 1 kế hoạch: các ngày tập, bài tập; hội viên bấm "Đã tập xong" từng ngày */
export function PlanView({ plan, onCompleteDay }) {
  return (
    <div className="stack">
      {plan.days.map((d) => (
        <div key={d.id} style={{ borderTop: '1px solid var(--border)', paddingTop: 12 }}>
          <div className="row" style={{ marginBottom: 8 }}>
            <b>Ngày {d.dayNumber}: {d.title}</b>
            <span className="spacer" />
            {d.completedAt
              ? <span className="up">✓ Đã tập</span>
              : onCompleteDay && <button className="btn success sm" onClick={() => onCompleteDay(d)}>Đã tập xong</button>}
          </div>
          <div className="table-wrap">
            <table>
              <tbody>
                {d.exercises.map((e) => (
                  <tr key={e.id}>
                    <td>{e.sortOrder}. {e.exerciseName} <span className="muted small">{label(e.muscleGroup)}</span></td>
                    <td className="right">
                      {e.sets ? `${e.sets} x ` : ''}{e.reps ? `${e.reps} lần` : `${e.durationSeconds}s`}
                      {e.weightKg ? ` · ${e.weightKg}kg` : ''}
                      {e.restSeconds ? <span className="muted small"> · nghỉ {e.restSeconds}s</span> : ''}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ))}
    </div>
  );
}
