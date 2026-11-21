import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';

import { api } from '../../api/client';
import type { Shift, ShiftAssignment } from '../../api/types';
import { ErrorNotice } from '../../components/ErrorNotice';
import { ReasonDialog } from '../../components/ReasonDialog';
import { DataTable, Field, FormModal, PageHead, StatusTag, Tabs, num, useAction, Stepper } from '../../components/ui';
import { addDays, businessToday, day, hm, weekStart, weekdayLabel } from '../../lib/format';
import { ASSIGNMENT_STATUS, span, useActiveEmployees, useAssignments } from './shared';

type AssignmentForm = { employeeIds: number[]; shiftId: number | ''; workDate: string; note: string };

/** Ca làm (khung giờ mẫu) và lịch phân ca theo tuần. Trùng giờ với ca khác của cùng người bị từ chối. */
export function ShiftsPage() {
  const [tab, setTab] = useState<'week' | 'shifts'>('week');
  const today = businessToday();
  const [monday, setMonday] = useState(weekStart(today));
  const [employeeId, setEmployeeId] = useState<number | ''>('');
  const sunday = addDays(monday, 6);
  const days = Array.from({ length: 7 }, (_, i) => addDays(monday, i));

  const shifts = useQuery({ queryKey: ['shifts', 'all'], queryFn: () => api.get<Shift[]>('/api/shifts?activeOnly=false') });
  const activeShifts = shifts.data?.filter((s) => s.active);
  const emps = useActiveEmployees();
  const assignments = useAssignments(monday, sunday, employeeId);

  const [shiftEdit, setShiftEdit] = useState<Shift | 'new' | null>(null);
  const [sf, setSf] = useState({ name: '', startTime: '08:00', endTime: '14:00', breakMinutes: '30', active: true });
  const [assigning, setAssigning] = useState(false);
  const [af, setAf] = useState<AssignmentForm>({ employeeIds: [], shiftId: '', workDate: today, note: '' });
  const [viewing, setViewing] = useState<ShiftAssignment | null>(null);
  const [cancelling, setCancelling] = useState(false);
  const unavailableEmployeeIds = af.employeeIds.filter((id) => !emps.data?.some((e) => e.id === id));

  const saveShift = useAction(
    () => {
      const body = { name: sf.name.trim(), startTime: sf.startTime, endTime: sf.endTime, breakMinutes: num(sf.breakMinutes) ?? 0, active: sf.active };
      return shiftEdit === 'new' ? api.post('/api/shifts', body) : api.put(`/api/shifts/${(shiftEdit as Shift).id}`, body);
    },
    { success: 'Đã lưu ca', invalidate: [['shifts']], onSuccess: () => setShiftEdit(null) },
  );
  const assign = useAction(
    (form: AssignmentForm) => api.post<ShiftAssignment[]>('/api/shift-assignments/bulk', { ...form, note: form.note.trim() || null }),
    {
      success: (created) => `Đã phân ca cho ${created.length} nhân viên`,
      invalidate: [['shift-assignments']],
      onSuccess: (_, form) => {
        setMonday(weekStart(form.workDate));
        if (employeeId !== '' && (form.employeeIds.length > 1 || !form.employeeIds.includes(employeeId))) setEmployeeId('');
        setAssigning(false);
      },
    },
  );
  const cancel = useAction((reason: string) => api.post(`/api/shift-assignments/${viewing!.id}/cancel`, { reason }), {
    success: 'Đã hủy phân ca', invalidate: [['shift-assignments']], onSuccess: () => { setCancelling(false); setViewing(null); },
  });

  const cancellable = viewing?.status === 'SCHEDULED' && viewing.attendanceId == null;

  // Lịch các tuần thường giống nhau: chép nguyên lịch tuần trước sang tuần đang xem, bỏ qua ca đã hủy, người đã nghỉ
  // việc hoặc ca trùng giờ (server từ chối từng ca, các ca khác vẫn được chép).
  const copyWeek = useAction(
    async () => {
      const prev = await api.get<ShiftAssignment[]>(`/api/shift-assignments?from=${addDays(monday, -7)}&to=${addDays(monday, -1)}`);
      let ok = 0;
      let skipped = 0;
      for (const a of prev.filter((x) => x.status !== 'CANCELLED')) {
        try {
          await api.post('/api/shift-assignments', { employeeId: a.employeeId, shiftId: a.shiftId, workDate: addDays(a.workDate, 7), note: null });
          ok += 1;
        } catch {
          skipped += 1;
        }
      }
      return { ok, skipped, empty: prev.length === 0 };
    },
    {
      success: (r) => (r.empty ? 'Tuần trước không có ca nào để chép' : `Đã chép ${r.ok} ca${r.skipped ? `, bỏ qua ${r.skipped} ca không phân được` : ''}`),
      invalidate: [['shift-assignments']],
    },
  );
  const weekEmpty = assignments.isSuccess && employeeId === '' && assignments.data.filter((a) => a.status !== 'CANCELLED').length === 0;

  function openShift(s: Shift | 'new') {
    setSf(s === 'new' ? { name: '', startTime: '08:00', endTime: '14:00', breakMinutes: '30', active: true }
      : { name: s.name, startTime: s.startTime.slice(0, 5), endTime: s.endTime.slice(0, 5), breakMinutes: String(s.breakMinutes), active: s.active });
    saveShift.reset();
    setShiftEdit(s);
  }
  function openAssign(date: string) {
    setAf({ employeeIds: employeeId === '' ? [] : [employeeId], shiftId: '', workDate: date, note: '' });
    assign.reset();
    setAssigning(true);
  }

  function toggleEmployee(id: number, checked: boolean) {
    setAf((form) => ({
      ...form,
      employeeIds: checked ? [...form.employeeIds, id] : form.employeeIds.filter((selected) => selected !== id),
    }));
  }

  return (
    <main className="main">
      <PageHead title="Ca và phân ca"
                actions={tab === 'week'
                  ? <button className="btn btn-primary" disabled={assign.isPending} onClick={() => openAssign(today)}>Phân ca</button>
                  : <button className="btn btn-primary" onClick={() => openShift('new')}>Thêm ca</button>} />
      <Tabs value={tab} onChange={setTab} options={[['week', 'Lịch tuần'], ['shifts', 'Ca làm']]} />

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
            <div className="toolbar">
              <Stepper unit="Tuần" onPrev={() => setMonday(addDays(monday, -7))} onNext={() => setMonday(addDays(monday, 7))}>
                <b className="stepper-label num">{day(monday)} – {day(sunday)}</b>
              </Stepper>
              {monday !== weekStart(today) && <button className="btn btn-sm" onClick={() => setMonday(weekStart(today))}>Tuần này</button>}
            </div>
            <Field label="Nhân viên">
              <select className="input" value={employeeId} onChange={(e) => setEmployeeId(e.target.value ? Number(e.target.value) : '')}>
                <option value="">Tất cả</option>
                {emps.data?.map((e) => <option key={e.id} value={e.id}>{e.fullName}</option>)}
              </select>
            </Field>
          </div>
          <ErrorNotice error={assignments.error ?? copyWeek.error} />
          {weekEmpty && monday >= weekStart(today) && (
            <div className="notice row-between" style={{ marginBottom: '1rem' }}>
              <span>Tuần này chưa phân ca.</span>
              <button className="btn btn-primary" disabled={copyWeek.isPending} onClick={() => copyWeek.mutate(undefined)}>
                {copyWeek.isPending ? 'Đang chép…' : 'Chép lịch tuần trước'}
              </button>
            </div>
          )}
          <div className="week">
            {days.map((d) => (
              <section key={d} className="week-day" data-today={d === today} aria-label={weekdayLabel(d)}>
                <div className="row-between">
                  <h3>{weekdayLabel(d)}</h3>
                  <button className="btn btn-sm btn-quiet" disabled={assign.isPending} onClick={() => openAssign(d)} aria-label={`Phân ca ngày ${day(d)}`}>+</button>
                </div>
                {(assignments.data ?? []).filter((a) => a.workDate === d).map((a) => (
                  <button key={a.id} type="button" className="shift-chip" data-status={a.status} onClick={() => setViewing(a)}>
                    <b>{a.employeeName}</b>
                    <span>{a.shiftName} · {span(a)}</span>
                  </button>
                ))}
              </section>
            ))}
          </div>
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

      <FormModal open={assigning} title="Phân ca" submitLabel="Phân ca" busy={assign.isPending}
                 submitDisabled={af.employeeIds.length === 0 || unavailableEmployeeIds.length > 0
                   || !activeShifts?.some((s) => s.id === af.shiftId) || !af.workDate}
                 error={assign.error ?? emps.error ?? shifts.error} onSubmit={() => assign.mutate(af)} onClose={() => setAssigning(false)}>
        <div className="form-grid">
          <fieldset className="assignment-employees span-2" disabled={assign.isPending}>
            <legend className="field-label">Nhân viên</legend>
            {emps.isPending && <p className="hint" role="status">Đang tải nhân viên…</p>}
            {emps.data?.length === 0 && <p className="hint">Không có nhân viên đang làm việc.</p>}
            {emps.data && (emps.data.length > 0 || unavailableEmployeeIds.length > 0) && (
              <>
                <p className="hint" role="status">Đã chọn {af.employeeIds.length} nhân viên</p>
                <div className="assignment-employee-list">
                  {unavailableEmployeeIds.map((id) => (
                    <label className="check" key={id}>
                      <input type="checkbox" checked onChange={() => toggleEmployee(id, false)} />
                      <span>Nhân viên #{id} <span className="hint">(không còn làm việc)</span></span>
                    </label>
                  ))}
                  {emps.data.map((e) => (
                    <label className="check" key={e.id}>
                      <input type="checkbox" checked={af.employeeIds.includes(e.id)} onChange={(event) => toggleEmployee(e.id, event.target.checked)} />
                      <span>{e.fullName} <span className="hint">({e.employeeCode})</span></span>
                    </label>
                  ))}
                </div>
              </>
            )}
          </fieldset>
          <Field label="Ca">
            <select className="input" required disabled={assign.isPending || !activeShifts?.length} value={af.shiftId} onChange={(e) => setAf({ ...af, shiftId: e.target.value ? Number(e.target.value) : '' })}>
              <option value="">{activeShifts?.length === 0 ? 'Không có ca đang dùng' : 'Chọn…'}</option>
              {activeShifts?.map((s) => <option key={s.id} value={s.id}>{s.name} ({s.startTime.slice(0, 5)}–{s.endTime.slice(0, 5)})</option>)}
            </select>
          </Field>
          <Field label="Ngày làm"><input className="input" type="date" required disabled={assign.isPending} value={af.workDate} onChange={(e) => setAf({ ...af, workDate: e.target.value })} /></Field>
          <Field label="Ghi chú" span><input className="input" maxLength={255} disabled={assign.isPending} value={af.note} onChange={(e) => setAf({ ...af, note: e.target.value })} /></Field>
        </div>
      </FormModal>

      <FormModal open={viewing != null && !cancelling} title={viewing ? `${viewing.employeeName} · ${viewing.shiftName}` : ''}
                 submitLabel={cancellable ? 'Hủy phân ca' : 'Đóng'} danger={cancellable} onClose={() => setViewing(null)}
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
