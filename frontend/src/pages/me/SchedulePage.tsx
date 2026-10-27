import { useState } from 'react';

import { ErrorNotice } from '../../components/ErrorNotice';
import { PageHead } from '../../components/ui';
import { addDays, businessToday, clock, day, hm, weekStart, weekdayLabel } from '../../lib/format';
import { ASSIGNMENT_STATUS, span, useAssignments, useAttendances } from '../staff/shared';

/** Lịch làm và công của chính người đăng nhập (backend tự giới hạn theo tài khoản). */
export function SchedulePage() {
  const today = businessToday();
  const [monday, setMonday] = useState(weekStart(today));
  const sunday = addDays(monday, 6);
  const assignments = useAssignments(monday, sunday);
  const attendances = useAttendances(monday, sunday);
  const att = new Map((attendances.data ?? []).map((a) => [a.shiftAssignmentId, a]));
  const list = (assignments.data ?? []).filter((a) => a.status !== 'CANCELLED');
  const worked = list.reduce((s, a) => s + (att.get(a.id)?.workedMinutes ?? 0), 0);

  return (
    <main className="main">
      <PageHead title="Lịch làm của tôi" subtitle={`${day(monday)} – ${day(sunday)} · ${list.length} ca · đã chấm ${hm(worked)}`}
                actions={<>
                  <button className="btn btn-sm" onClick={() => setMonday(addDays(monday, -7))}>Tuần trước</button>
                  {monday !== weekStart(today) && <button className="btn btn-sm" onClick={() => setMonday(weekStart(today))}>Tuần này</button>}
                  <button className="btn btn-sm" onClick={() => setMonday(addDays(monday, 7))}>Tuần sau</button>
                </>} />
      <ErrorNotice error={assignments.error ?? attendances.error} />
      <div className="week">
        {Array.from({ length: 7 }, (_, i) => addDays(monday, i)).map((d) => (
          <section key={d} className="week-day" data-today={d === today}>
            <h3>{weekdayLabel(d)}</h3>
            {(assignments.data ?? []).filter((a) => a.workDate === d).map((a) => {
              const x = att.get(a.id);
              return (
                <div key={a.id} className="shift-chip" data-status={a.status} style={{ cursor: 'default' }}>
                  <b>{a.shiftName}</b>
                  <span>{span(a)}</span>
                  <span className="muted">
                    {x ? (x.status === 'ABSENT' ? 'Vắng' : `Vào ${clock(x.actualStartAt!)} · ra ${clock(x.actualEndAt!)} · ${hm(x.workedMinutes)}`) : ASSIGNMENT_STATUS[a.status]}
                  </span>
                </div>
              );
            })}
          </section>
        ))}
      </div>
    </main>
  );
}
