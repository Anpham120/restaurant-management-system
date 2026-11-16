import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';

import { api } from '../../api/client';
import type { Attendance, AttendanceAdjustment, ShiftAssignment } from '../../api/types';
import { ErrorNotice } from '../../components/ErrorNotice';
import { DataTable, Field, FormModal, PageHead, StatusTag, num, useAction, Stepper } from '../../components/ui';
import { addDays, businessToday, clock, dateTime, day, fromLocalInput, hm, toLocalInput } from '../../lib/format';
import { span, useAssignments, useAttendances } from './shared';

type Form = { status: 'PRESENT' | 'ABSENT'; start: string; end: string; worked: string; note: string; reason: string };

/** Chấm công theo ngày: mỗi ca chấm một lần; sửa sau đó phải ghi lý do và được lưu lịch sử. */
export function AttendancePage() {
  const [date, setDate] = useState(businessToday());
  const assignments = useAssignments(date, date);
  const attendances = useAttendances(date, date);
  const [target, setTarget] = useState<{ a: ShiftAssignment; att?: Attendance } | null>(null);
  const [form, setForm] = useState<Form>({ status: 'PRESENT', start: '', end: '', worked: '', note: '', reason: '' });

  const history = useQuery({
    queryKey: ['attendance-adjustments', target?.att?.id],
    queryFn: () => api.get<AttendanceAdjustment[]>(`/api/attendances/${target!.att!.id}/adjustments`),
    enabled: target?.att != null,
  });
  const keys = [['attendances'], ['shift-assignments'], ['attendance-adjustments']];
  const save = useAction(
    () => {
      const body = {
        status: form.status,
        actualStartAt: form.status === 'PRESENT' ? fromLocalInput(form.start) : null,
        actualEndAt: form.status === 'PRESENT' ? fromLocalInput(form.end) : null,
        workedMinutes: form.status === 'PRESENT' ? num(form.worked) : null,
      };
      return target!.att
        ? api.post(`/api/attendances/${target!.att.id}/adjustments`, { ...body, reason: form.reason.trim() })
        : api.post('/api/attendances', { ...body, shiftAssignmentId: target!.a.id, note: form.note.trim() || null });
    },
    { success: () => (target?.att ? 'Đã điều chỉnh công' : 'Đã chấm công'), invalidate: keys, onSuccess: () => setTarget(null) },
  );

  // Một chạm: có mặt đúng giờ của ca (công = thời gian ca trừ giờ nghỉ). Ca nào khác lịch thì mở form "Khác…".
  const present = (a: ShiftAssignment) =>
    api.post('/api/attendances', { shiftAssignmentId: a.id, status: 'PRESENT', actualStartAt: a.scheduledStartAt, actualEndAt: a.scheduledEndAt, workedMinutes: null, note: null });
  const quick = useAction(present, { success: (_r, a) => `Đã chấm ${a.employeeName} có mặt đủ ca`, invalidate: keys });
  const quickAll = useAction(
    async (list: ShiftAssignment[]) => {
      for (const a of list) await present(a);
      return list.length;
    },
    { success: (n) => `Đã chấm ${n} ca có mặt đủ ca`, invalidate: keys },
  );

  function open(a: ShiftAssignment, att?: Attendance) {
    setForm({
      status: att?.status ?? 'PRESENT',
      start: toLocalInput(att?.actualStartAt ?? a.scheduledStartAt),
      end: toLocalInput(att?.actualEndAt ?? a.scheduledEndAt),
      worked: att?.workedMinutes != null ? String(att.workedMinutes) : '',
      note: '',
      reason: '',
    });
    save.reset();
    setTarget({ a, att });
  }

  const attByAssignment = new Map((attendances.data ?? []).map((x) => [x.shiftAssignmentId, x]));
  const rows = (assignments.data ?? []).filter((a) => a.status !== 'CANCELLED');
  // Chỉ chấm nhanh ca đã bắt đầu: ca chưa tới giờ thì chưa biết có đi làm không
  const started = (a: ShiftAssignment) => new Date(a.scheduledStartAt).getTime() <= Date.now();
  const unmarked = rows.filter((a) => !attByAssignment.has(a.id) && started(a));

  return (
    <main className="main">
      <PageHead title="Chấm công" subtitle={`Ngày ${day(date)} · ${rows.length} ca`}
                actions={<>
                  <Stepper unit="Ngày" onPrev={() => setDate(addDays(date, -1))} onNext={() => setDate(addDays(date, 1))}>
                    <input className="input" type="date" aria-label="Ngày" value={date} onChange={(e) => e.target.value && setDate(e.target.value)} />
                  </Stepper>
                  {unmarked.length > 1 && (
                    <button className="btn btn-primary" disabled={quickAll.isPending} onClick={() => quickAll.mutate(unmarked)}>
                      Chấm {unmarked.length} ca có mặt đủ ca
                    </button>
                  )}
                </>} />
      <ErrorNotice error={assignments.error ?? attendances.error ?? quick.error ?? quickAll.error} />
      <DataTable rows={assignments.data ? rows : undefined} rowKey={(a) => a.id} empty="Không có ca nào được phân trong ngày này."
        columns={[
          { header: 'Nhân viên', render: (a) => <b>{a.employeeName}</b> },
          { header: 'Ca', render: (a) => `${a.shiftName} · ${span(a)}` },
          {
            header: 'Thực tế',
            render: (a) => {
              const att = attByAssignment.get(a.id);
              if (!att) return <StatusTag tone="wait">Chưa chấm</StatusTag>;
              if (att.status === 'ABSENT') return <StatusTag tone="stop">Vắng</StatusTag>;
              return `${clock(att.actualStartAt!)}–${clock(att.actualEndAt!)}`;
            },
          },
          { header: 'Công', render: (a) => hm(attByAssignment.get(a.id)?.workedMinutes), align: 'right' },
          {
            header: '',
            render: (a) => {
              const att = attByAssignment.get(a.id);
              if (att) return <button className="btn btn-sm" onClick={() => open(a, att)}>Điều chỉnh</button>;
              return (
                <div className="toolbar">
                  {started(a) && <button className="btn btn-sm btn-primary" disabled={quick.isPending || quickAll.isPending} onClick={() => quick.mutate(a)}>Có mặt đủ ca</button>}
                  <button className="btn btn-sm" onClick={() => open(a)}>Khác…</button>
                </div>
              );
            },
          },
        ]} />

      <FormModal open={target != null} wide title={target ? `${target.att ? 'Điều chỉnh công' : 'Chấm công'}: ${target.a.employeeName}` : ''}
                 submitLabel={target?.att ? 'Lưu điều chỉnh' : 'Chấm công'} busy={save.isPending} error={save.error}
                 onSubmit={() => save.mutate(undefined)} onClose={() => setTarget(null)}
                 description={target ? `${target.a.shiftName} · lịch ${span(target.a)} · nghỉ ${target.a.scheduledBreakMinutes} phút. Công để trống = thời gian có mặt trừ giờ nghỉ.` : undefined}>
        <div className="form-grid">
          <Field label="Tình trạng" span>
            <select className="input" value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value as Form['status'] })}>
              <option value="PRESENT">Có mặt</option><option value="ABSENT">Vắng</option>
            </select>
          </Field>
          {form.status === 'PRESENT' && (
            <>
              <Field label="Giờ vào"><input className="input" type="datetime-local" required value={form.start} onChange={(e) => setForm({ ...form, start: e.target.value })} /></Field>
              <Field label="Giờ ra"><input className="input" type="datetime-local" required value={form.end} onChange={(e) => setForm({ ...form, end: e.target.value })} /></Field>
              <Field label="Số phút công"><input className="input" type="number" min={0} value={form.worked} onChange={(e) => setForm({ ...form, worked: e.target.value })} /></Field>
            </>
          )}
          {target?.att
            ? <Field label="Lý do điều chỉnh" span><input className="input" required maxLength={255} value={form.reason} onChange={(e) => setForm({ ...form, reason: e.target.value })} /></Field>
            : <Field label="Ghi chú" span><input className="input" maxLength={255} value={form.note} onChange={(e) => setForm({ ...form, note: e.target.value })} /></Field>}
        </div>
        {target?.att && history.data && history.data.length > 0 && (
          <div className="stack">
            <h2>Lịch sử điều chỉnh</h2>
            {history.data.map((h) => (
              <div key={h.id} style={{ fontSize: 'var(--step--1)' }}>
                <b>{dateTime(h.adjustedAt)}</b> · {h.reason}<br />
                <span className="muted">
                  {h.oldStatus === 'PRESENT' ? `${clock(h.oldStartAt!)}–${clock(h.oldEndAt!)}, ${hm(h.oldWorkedMinutes)}` : 'Vắng'} → {h.newStatus === 'PRESENT' ? `${clock(h.newStartAt!)}–${clock(h.newEndAt!)}, ${hm(h.newWorkedMinutes)}` : 'Vắng'}
                </span>
              </div>
            ))}
          </div>
        )}
      </FormModal>
    </main>
  );
}
