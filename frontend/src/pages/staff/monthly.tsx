import type { ReactNode } from 'react';

import { Stepper } from '../../components/ui';

export const WEEKDAYS = [
  [1, 'Thứ Hai'], [2, 'Thứ Ba'], [3, 'Thứ Tư'], [4, 'Thứ Năm'],
  [5, 'Thứ Sáu'], [6, 'Thứ Bảy'], [0, 'Chủ nhật'],
] as const;

const MONTH = /^(?!0000)\d{4}-(0[1-9]|1[0-2])$/;

export function weekdayOf(date: string): number {
  return new Date(date + 'T00:00:00Z').getUTCDay();
}

export function daysInMonth(month: string): string[] {
  const last = new Date(month + '-01T00:00:00Z');
  last.setUTCMonth(last.getUTCMonth() + 1, 0);
  return Array.from({ length: last.getUTCDate() }, (_, index) => `${month}-${String(index + 1).padStart(2, '0')}`);
}

function stepMonth(month: string, delta: number): string {
  const date = new Date(month + '-01T00:00:00Z');
  date.setUTCMonth(date.getUTCMonth() + delta);
  const next = date.toISOString().slice(0, 7);
  return MONTH.test(next) ? next : month;
}

export function monthLabel(month: string): string {
  return `Tháng ${month.slice(5)}/${month.slice(0, 4)}`;
}

export function MonthPicker({ month, currentMonth, onChange }: {
  month: string;
  currentMonth: string;
  onChange: (month: string) => void;
}) {
  return (
    <div className="toolbar">
      <Stepper unit="Tháng" onPrev={() => onChange(stepMonth(month, -1))} onNext={() => onChange(stepMonth(month, 1))}>
        <input type="month" className="input" aria-label="Tháng xem lịch" value={month} min="0001-01" max="9999-12"
               onChange={(event) => { if (MONTH.test(event.target.value)) onChange(event.target.value); }} />
      </Stepper>
      {month !== currentMonth && <button type="button" className="btn btn-sm" onClick={() => onChange(currentMonth)}>Tháng này</button>}
    </div>
  );
}

export function MonthCalendar({ firstDate, children }: { firstDate: string; children: ReactNode }) {
  const offset = (weekdayOf(firstDate) + 6) % 7;
  return (
    <div className="month-calendar">
      {Array.from({ length: offset }, (_, index) => <div key={index} className="month-calendar-spacer" aria-hidden="true" />)}
      {children}
    </div>
  );
}
