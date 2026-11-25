import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';

import { api } from '../../api/client';
import type { Shift, ShiftAssignment } from '../../api/types';
import { ErrorNotice } from '../../components/ErrorNotice';
import { ReasonDialog } from '../../components/ReasonDialog';
import { DataTable, Field, FormModal, PageHead, StatusTag, Tabs, num, useAction } from '../../components/ui';
import { businessToday, day, hm, weekdayLabel } from '../../lib/format';
import { MonthlyAssignmentModal } from './MonthlyAssignmentModal';
import { daysInMonth, MonthCalendar, MonthPicker } from './monthly';
import { assignmentsByDate, ASSIGNMENT_STATUS, span, useActiveEmployees, useAssignments } from './shared';

/** Ca làm (khung giờ mẫu) và lịch phân ca theo tháng. */
export function ShiftsPage() {
  const [tab, setTab] = useState<'month' | 'shifts'>('month');
  const today = businessToday();
  const currentMonth = today.slice(0, 7);
  const [month, setMonth] = useState(currentMonth);
  const [employeeId, setEmployeeId] = useState<number | ''>('');
  const days = daysInMonth(month);

  const shifts = useQuery({ queryKey: ['shifts', 'all'], queryFn: () => api.get<Shift[]>('/api/shifts?activeOnly=false') });
  const activeShifts = shifts.data?.filter((s) => s.active);
  const emps = useActiveEmployees();
  const assignments = useAssignments(days[0], days[days.length - 1], employeeId);
  const byDate = assignmentsByDate(assignments.data);

  const [shiftEdit, setShiftEdit] = useState<Shift | 'new' | null>(null);
  const [sf, setSf] = useState({ name: '', startTime: '08:00', endTime: '14:00', breakMinutes: '30', active: true });
  const [assigning, setAssigning] = useState(false);
  const [viewing, setViewing] = useState<ShiftAssignment | null>(null);
  const [cancelling, setCancelling] = useState(false);

  const saveShift = useAction(
    () => {
      const body = { name: sf.name.trim(), startTime: sf.startTime, endTime: sf.endTime, breakMinutes: num(sf.breakMinutes) ?? 0, active: sf.active };
      return shiftEdit === 'new' ? api.post('/api/shifts', body) : api.put(`/api/shifts/${(shiftEdit as Shift).id}`, body);
    },
    { success: 'Đã lưu ca', invalidate: [['shifts']], onSuccess: () => setShiftEdit(null) },
  );
  const cancel = useAction((reason: string) => api.post(`/api/shift-assignments/${viewing!.id}/cancel`, { reason }), {
    success: 'Đã hủy phân ca', invalidate: [['shift-assignments']], onSuccess: () => { setCancelling(false); setViewing(null); },
  });

  const cancellable = viewing?.status === 'SCHEDULED' && viewing.attendanceId == null;

  function openShift(s: Shift | 'new') {
    setSf(s === 'new' ? { name: '', startTime: '08:00', endTime: '14:00', breakMinutes: '30', active: true }
      : { name: s.name, startTime: s.startTime.slice(0, 5), endTime: s.endTime.slice(0, 5), breakMinutes: String(s.breakMinutes), active: s.active });
    saveShift.reset();
    setShiftEdit(s);
  }
  return (
    <main className="main">
      <PageHead title="Ca và phân ca"
                actions={tab === 'month'
                  ? <button type="button" className="btn btn-primary" onClick={() => setAssigning(true)}>Phân ca theo tháng</button>
                  : <button className="btn btn-primary" onClick={() => openShift('new')}>Thêm ca</button>} />
      <Tabs value={tab} onChange={setTab} options={[['month', 'Lịch tháng'], ['shifts', 'Ca làm']]} />

      {tab === 'shifts' ? (
        <DataTable rows={shifts.data} rowKey={(s) => s.id} empty="Chưa có ca làm."
          columns={[
            { header: 'Ca', render: (s) => <b>{s.name}</b> },
            { header: 'Giờ', render: (s) => `${s.startTime.slice(0, 5)}–${s.endTime.slice(0, 5)}${s.endTime <= s.startTime ? ' (qua đêm)' : ''}` },
            { header: 'Nghỉ giữa ca', render: (s) => `${s.breakMinutes} phút`, align: 'right' },
            { header: 'Trạng thái', render: (s) => (s.active ? 'Đang dùng' : <StatusTag tone="stop">Ngừng</StatusTag>) },
            { header: '', render: (s) => <button className="btn btn-sm" onClick={() => openShift(s)}>Sửa</button> },
          ]} />
      ) : (
        <>
          <div className="filters">
            <MonthPicker month={month} currentMonth={currentMonth} onChange={setMonth} />
            <Field label="Nhân viên">
              <select className="input" value={employeeId} onChange={(e) => setEmployeeId(e.target.value ? Number(e.target.value) : '')}>
                <option value="">Tất cả</option>
                {emps.data?.map((e) => <option key={e.id} value={e.id}>{e.fullName}</option>)}
              </select>
            </Field>
          </div>
          <ErrorNotice error={assignments.error ?? emps.error} />
          {assignments.isPending && <p className="muted" role="status">Đang tải lịch tháng…</p>}
          {assignments.data?.length === 0 && <p className="muted">Tháng này chưa có ca được phân.</p>}
          <MonthCalendar firstDate={days[0]}>
            {days.map((d) => (
              <section key={d} className="week-day" data-today={d === today} aria-label={weekdayLabel(d)}>
                <h3>{weekdayLabel(d)}</h3>
                {byDate.get(d)?.map((a) => (
                  <button key={a.id} type="button" className="shift-chip" data-status={a.status} onClick={() => setViewing(a)}>
                    <b>{a.employeeName}</b>
                    <span>{a.shiftName} · {span(a)}</span>
                  </button>
                ))}
              </section>
            ))}
          </MonthCalendar>
        </>
      )}

      <FormModal open={shiftEdit != null} title={shiftEdit === 'new' ? 'Thêm ca' : 'Sửa ca'} submitLabel="Lưu ca" busy={saveShift.isPending} error={saveShift.error}
                 onSubmit={() => saveShift.mutate(undefined)} onClose={() => setShiftEdit(null)} description="Sửa giờ ca chỉ áp dụng cho các lần phân ca sau.">
        <div className="form-grid">
          <Field label="Tên ca" span><input className="input" required maxLength={50} value={sf.name} onChange={(e) => setSf({ ...sf, name: e.target.value })} autoFocus /></Field>
          <Field label="Bắt đầu"><input className="input" type="time" required value={sf.startTime} onChange={(e) => setSf({ ...sf, startTime: e.target.value })} /></Field>
          <Field label="Kết thúc"><input className="input" type="time" required value={sf.endTime} onChange={(e) => setSf({ ...sf, endTime: e.target.value })} /></Field>
          <Field label="Nghỉ giữa ca (phút)"><input className="input" type="number" min={0} max={600} value={sf.breakMinutes} onChange={(e) => setSf({ ...sf, breakMinutes: e.target.value })} /></Field>
          <label className="check"><input type="checkbox" checked={sf.active} onChange={(e) => setSf({ ...sf, active: e.target.checked })} />Đang dùng</label>
        </div>
      </FormModal>

      {assigning && (
        <MonthlyAssignmentModal month={month} employees={emps.data} shifts={activeShifts} initialEmployee={employeeId}
                                loadingError={emps.error ?? shifts.error} onClose={() => setAssigning(false)}
                                onSaved={(ids) => {
                                  if (employeeId !== '' && (ids.length > 1 || !ids.includes(employeeId))) setEmployeeId('');
                                  setAssigning(false);
                                }} />
      )}

      <FormModal open={viewing != null && !cancelling} title={viewing ? `${viewing.employeeName} · ${viewing.shiftName}` : ''}
                 submitLabel={cancellable ? 'Hủy phân ca' : 'Đóng'} danger={cancellable}
                 onClose={() => { if (!cancelling) setViewing(null); }}
                 onSubmit={() => (cancellable ? setCancelling(true) : setViewing(null))}
                 description={viewing && viewing.status !== 'SCHEDULED' ? 'Chỉ hủy được ca chưa chấm công.' : undefined}>
        {viewing && (
          <dl className="kv">
            <dt>Ngày</dt><dd>{day(viewing.workDate)}</dd>
            <dt>Giờ</dt><dd>{span(viewing)}</dd>
            <dt>Nghỉ giữa ca</dt><dd>{hm(viewing.scheduledBreakMinutes)}</dd>
            <dt>Trạng thái</dt><dd>{ASSIGNMENT_STATUS[viewing.status]}</dd>
            {viewing.note && <><dt>Ghi chú</dt><dd>{viewing.note}</dd></>}
            {viewing.cancelReason && <><dt>Lý do hủy</dt><dd>{viewing.cancelReason}</dd></>}
          </dl>
        )}
      </FormModal>
      <ReasonDialog open={cancelling} title="Hủy phân ca?" description="Khung giờ này có thể phân lại cho nhân viên." confirmLabel="Hủy phân ca" danger suggestions={['Nhân viên xin nghỉ', 'Đổi ca', 'Phân nhầm']}
                    onConfirm={(r) => cancel.mutate(r)} onClose={() => setCancelling(false)} />
      <ErrorNotice error={cancel.error} />
    </main>
  );
}
