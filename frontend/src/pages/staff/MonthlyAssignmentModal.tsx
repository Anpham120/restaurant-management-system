import { useState } from 'react';

import { api } from '../../api/client';
import type { Employee, Shift } from '../../api/types';
import { Field, FormModal, useAction } from '../../components/ui';
import { day, weekdayLabel } from '../../lib/format';
import { daysInMonth, monthLabel, weekdayOf, WEEKDAYS } from './monthly';

type DayPlan = { workDate: string; shiftId: number };
type MonthlyResult = { created: number; unchanged: number };
type Override = number | 'off';

function shiftLabel(shift: Shift): string {
  return `${shift.name} (${shift.startTime.slice(0, 5)}–${shift.endTime.slice(0, 5)})`;
}

export function MonthlyAssignmentModal({ month, initialEmployee, employees, shifts, loadingError, onSaved, onClose }: {
  month: string;
  initialEmployee: number | '';
  employees: Employee[] | undefined;
  shifts: Shift[] | undefined;
  loadingError: unknown;
  onSaved: (employeeIds: number[]) => void;
  onClose: () => void;
}) {
  const [employeeIds, setEmployeeIds] = useState<number[]>(initialEmployee === '' ? [] : [initialEmployee]);
  const [weekPattern, setWeekPattern] = useState<Partial<Record<number, number>>>({});
  const [overrides, setOverrides] = useState<Partial<Record<string, Override>>>({});
  const [preview, setPreview] = useState(false);
  const [note, setNote] = useState('');

  const dates = daysInMonth(month);
  const availableShifts = new Map((shifts ?? []).map((shift) => [shift.id, shift]));
  const unavailableEmployees = employeeIds.filter((id) => !employees?.some((employee) => employee.id === id));
  const days = dates.flatMap((date): DayPlan[] => {
    const shift = overrides[date] ?? weekPattern[weekdayOf(date)];
    return typeof shift === 'number' ? [{ workDate: date, shiftId: shift }] : [];
  });
  const unavailableShifts = days.some(({ shiftId }) => !availableShifts.has(shiftId));

  const save = useAction(
    () => api.post<MonthlyResult>('/api/shift-assignments/monthly', { employeeIds, month, days, note: note.trim() || null }),
    {
      success: ({ created, unchanged }) => `Đã lưu ${created} ca mới${unchanged ? `, giữ ${unchanged} ca đã có` : ''}`,
      invalidate: [['shift-assignments']],
      onSuccess: () => onSaved(employeeIds),
    },
  );
  const shiftOptions = (shifts ?? []).map((shift) => <option key={shift.id} value={shift.id}>{shiftLabel(shift)}</option>);

  function toggleEmployee(id: number, selected: boolean) {
    setEmployeeIds((ids) => selected ? [...ids, id] : ids.filter((existing) => existing !== id));
  }

  function setDayOverride(date: string, value: string) {
    setOverrides((previous) => {
      const next = { ...previous };
      if (value === 'template') delete next[date];
      else next[date] = value === 'off' ? 'off' : Number(value);
      return next;
    });
  }

  return (
    <FormModal open wide title="Phân ca theo tháng" description={monthLabel(month)} submitLabel="Lưu lịch tháng"
               busy={save.isPending} preventCloseWhileBusy
               submitDisabled={!preview || employeeIds.length === 0 || unavailableEmployees.length > 0 || days.length === 0 || unavailableShifts}
               error={save.error ?? loadingError} onSubmit={() => save.mutate(undefined)} onClose={onClose}>
      <fieldset className="assignment-employees" disabled={save.isPending}>
        <legend className="field-label">Nhân viên</legend>
        {!employees && !loadingError && <p className="hint" role="status">Đang tải nhân viên…</p>}
        {employees?.length === 0 && <p className="hint">Không có nhân viên đang làm việc.</p>}
        {employees && (employees.length > 0 || unavailableEmployees.length > 0) && (
          <>
            <p className="hint" role="status">Đã chọn {employeeIds.length} nhân viên</p>
            <div className="assignment-employee-list">
              {unavailableEmployees.map((id) => (
                <label className="check" key={id}>
                  <input type="checkbox" checked onChange={() => toggleEmployee(id, false)} />
                  <span>Nhân viên #{id} <span className="hint">(không còn làm việc)</span></span>
                </label>
              ))}
              {employees.map((employee) => (
                <label className="check" key={employee.id}>
                  <input type="checkbox" checked={employeeIds.includes(employee.id)}
                         onChange={(event) => toggleEmployee(employee.id, event.target.checked)} />
                  <span>{employee.fullName} <span className="hint">({employee.employeeCode})</span></span>
                </label>
              ))}
            </div>
          </>
        )}
      </fieldset>

      <fieldset className="assignment-employees stack" disabled={save.isPending}>
        <legend className="field-label">Mẫu ca theo thứ</legend>
        {shifts?.length === 0 && <p className="hint">Chưa có ca đang dùng.</p>}
        <div className="month-plan-grid">
          {WEEKDAYS.map(([weekday, label]) => (
            <Field key={weekday} label={label}>
              <select className="input" aria-label={label} disabled={!shifts?.length} value={weekPattern[weekday] ?? ''}
                      onChange={(event) => setWeekPattern((prev) => ({ ...prev, [weekday]: event.target.value ? Number(event.target.value) : undefined }))}>
                <option value="">Không phân ca</option>
                {shiftOptions}
              </select>
            </Field>
          ))}
        </div>
      </fieldset>

      {preview ? (
        <fieldset className="assignment-employees stack" disabled={save.isPending}>
          <legend className="field-label">Xem trước và chỉnh từng ngày</legend>
          <p className="hint" role="status">{days.length} ngày có ca × {employeeIds.length} nhân viên</p>
          <p className="hint">Ngày không phân ca giữ lịch hiện có.</p>
          {unavailableShifts && <p className="notice notice-warn" role="status">Có ca trong lịch đã ngừng dùng. Chọn ca khác để lưu.</p>}
          <div className="month-plan-grid month-plan-preview">
            {dates.map((date) => {
              const planned = weekPattern[weekdayOf(date)];
              const shift = planned != null ? availableShifts.get(planned) : undefined;
              return (
                <label key={date} className="field">
                  <span className="field-label">{weekdayLabel(date)}</span>
                  <select className="input" aria-label={`Ca ngày ${day(date)}`} disabled={!shifts?.length}
                          value={overrides[date] ?? 'template'} onChange={(event) => setDayOverride(date, event.target.value)}>
                    <option value="template">{shift ? `Theo mẫu · ${shiftLabel(shift)}` : planned != null ? 'Theo mẫu · Ca đã ngừng dùng' : 'Theo mẫu · Không phân ca'}</option>
                    <option value="off">Không phân ca ngày này</option>
                    {shiftOptions}
                  </select>
                </label>
              );
            })}
          </div>
        </fieldset>
      ) : <button type="button" className="btn" onClick={() => setPreview(true)}>Xem trước lịch tháng</button>}

      <Field label="Ghi chú">
        <input className="input" maxLength={255} disabled={save.isPending} value={note} onChange={(event) => setNote(event.target.value)} />
      </Field>
    </FormModal>
  );
}
