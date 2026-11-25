import { useState } from 'react';

import { useAuth } from '../../auth/AuthContext';
import { ErrorNotice } from '../../components/ErrorNotice';
import { PageHead } from '../../components/ui';
import { businessToday, clock, hm, weekdayLabel } from '../../lib/format';
import { daysInMonth, MonthCalendar, monthLabel, MonthPicker } from '../staff/monthly';
import { assignmentsByDate, ASSIGNMENT_STATUS, span, useAssignments, useAttendances } from '../staff/shared';

/** Lịch làm và công của chính người đăng nhập (backend tự giới hạn theo tài khoản). */
export function SchedulePage() {
  const { user } = useAuth();
  const today = businessToday();
  const currentMonth = today.slice(0, 7);
  const [month, setMonth] = useState(currentMonth);
  const days = daysInMonth(month);
  const lastDate = days[days.length - 1];
  const assignments = useAssignments(days[0], lastDate, user?.employeeId);
  const attendances = useAttendances(days[0], lastDate, user?.employeeId);
  const byDate = assignmentsByDate(assignments.data);
  const att = new Map((attendances.data ?? []).map((a) => [a.shiftAssignmentId, a]));
  const list = (assignments.data ?? []).filter((a) => a.status !== 'CANCELLED');
  const worked = list.reduce((s, a) => s + (att.get(a.id)?.workedMinutes ?? 0), 0);

  return (
    <main className="main">
      <PageHead title="Lịch làm của tôi" subtitle={`${monthLabel(month)} · ${list.length} ca · đã chấm ${hm(worked)}`}
                actions={<MonthPicker month={month} currentMonth={currentMonth} onChange={setMonth} />} />
      <ErrorNotice error={assignments.error ?? attendances.error} />
      {assignments.isPending && <p className="muted" role="status">Đang tải lịch tháng…</p>}
      {assignments.data?.length === 0 && <p className="muted">Tháng này chưa có ca được phân.</p>}
      <MonthCalendar firstDate={days[0]}>
        {days.map((d) => (
          <section key={d} className="week-day" data-today={d === today} aria-label={weekdayLabel(d)}>
            <h3>{weekdayLabel(d)}</h3>
            {byDate.get(d)?.map((a) => {
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
      </MonthCalendar>
    </main>
  );
}
