import { useState } from 'react';
import { Plus } from 'lucide-react';
import { api } from '../../api/client.js';
import PlanEditor, { PlanView } from '../../components/PlanEditor.jsx';
import { Async, Badge, Field, PageHead, useFetch } from '../../components/ui.jsx';

export default function TrainerPlansPage() {
  const plans = useFetch(() => api('/api/workout-plans/trainer/me'), []);
  const [editing, setEditing] = useState(null);
  const [open, setOpen] = useState(null);

  return (
    <>
      <PageHead title="Kế hoạch tập đã soạn">
        <button className="btn" onClick={() => setEditing('new')}><Plus size={16} /> Soạn kế hoạch</button>
      </PageHead>
      <Async state={plans} empty="Bạn chưa soạn kế hoạch nào">
        {(list) => list.map((p) => (
          <div key={p.id} className="card">
            <div className="card-head">
              <div>
                <h2>{p.name}</h2>
                <div className="muted small">Hội viên: {p.memberName}{p.goal ? ` · ${p.goal}` : ''}</div>
              </div>
              <div className="row">
                <Badge color="blue">{p.completedDays}/{p.totalDays} ngày đã tập</Badge>
                <Badge status={p.status} />
                <button className="btn secondary sm" onClick={() => setOpen(open === p.id ? null : p.id)}>{open === p.id ? 'Thu gọn' : 'Xem'}</button>
                <button className="btn secondary sm" onClick={() => setEditing(p)}>Sửa</button>
              </div>
            </div>
            {open === p.id && <PlanView plan={p} />}
          </div>
        ))}
      </Async>
      {editing && <TrainerPlanEditor plan={editing === 'new' ? null : editing} onClose={() => setEditing(null)} onSaved={plans.reload} />}
    </>
  );
}

/** Bọc PlanEditor thêm ô chọn hội viên (HLV soạn cho hội viên) */
function TrainerPlanEditor({ plan, onClose, onSaved }) {
  const [memberId, setMemberId] = useState(plan?.memberId || '');
  const [keyword, setKeyword] = useState('');
  const members = useFetch(() => api('/api/admin/members', { query: { keyword, size: 20 } }), [keyword]);

  const picker = plan ? (
    <div className="alert info">Hội viên: <b>{plan.memberName}</b></div>
  ) : (
    <div className="grid grid-2">
      <Field label="Tìm hội viên">
        <input className="input" value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder="Tên, email, SĐT" />
      </Field>
      <Field label="Hội viên">
        <select className="input" value={memberId} onChange={(e) => setMemberId(e.target.value)}>
          <option value="">-- Chọn hội viên --</option>
          {members.data?.content.map((m) => <option key={m.id} value={m.id}>{m.fullName} ({m.email})</option>)}
        </select>
      </Field>
    </div>
  );

  return <PlanEditor plan={plan} memberId={Number(memberId) || null} memberPicker={picker} onClose={onClose} onSaved={onSaved} />;
}
