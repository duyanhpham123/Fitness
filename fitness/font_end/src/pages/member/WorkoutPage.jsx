import { useState } from 'react';
import { Plus } from 'lucide-react';
import { api } from '../../api/client.js';
import PlanEditor, { PlanView } from '../../components/PlanEditor.jsx';
import { Async, Badge, PageHead, useFetch, useToast } from '../../components/ui.jsx';

export default function WorkoutPage() {
  const toast = useToast();
  const plans = useFetch(() => api('/api/workout-plans/me'), []);
  const [editing, setEditing] = useState(null);

  const completeDay = async (plan, day) => {
    try {
      const res = await api(`/api/workout-plans/${plan.id}/days/${day.id}/complete`, { method: 'POST' });
      toast(res.status === 'COMPLETED' ? 'Chúc mừng! Bạn đã hoàn thành kế hoạch' : `Đã hoàn thành ngày ${day.dayNumber}`, 'success');
      plans.reload();
    } catch (err) {
      toast(err);
    }
  };

  return (
    <>
      <PageHead title="Kế hoạch tập luyện" subtitle="Kế hoạch do HLV soạn hoặc bạn tự soạn">
        <button className="btn" onClick={() => setEditing('new')}><Plus size={16} /> Tự soạn kế hoạch</button>
      </PageHead>
      <Async state={plans} empty="Bạn chưa có kế hoạch tập nào. Hãy đặt lịch PT để HLV soạn cho bạn, hoặc tự soạn.">
        {(list) => list.map((p) => (
          <div key={p.id} className="card">
            <div className="card-head">
              <div>
                <h2>{p.name}</h2>
                <div className="muted small">
                  {p.trainerName ? `HLV ${p.trainerName}` : 'Tự soạn'}{p.goal ? ` · Mục tiêu: ${p.goal}` : ''}
                </div>
              </div>
              <div className="row">
                <Badge color="blue">{p.completedDays}/{p.totalDays} ngày</Badge>
                <Badge status={p.status} />
                {!p.trainerId && <button className="btn secondary sm" onClick={() => setEditing(p)}>Sửa</button>}
              </div>
            </div>
            <PlanView plan={p} onCompleteDay={p.status === 'ACTIVE' ? (d) => completeDay(p, d) : null} />
          </div>
        ))}
      </Async>
      {editing && (
        <PlanEditor plan={editing === 'new' ? null : editing} onClose={() => setEditing(null)} onSaved={plans.reload} />
      )}
    </>
  );
}
