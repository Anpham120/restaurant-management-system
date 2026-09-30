import dayjs, { type Dayjs } from 'dayjs'
import type { LeaveRequest, LeaveStatus, LeaveType, PayrollDetail, PayrollStatus } from '../api/types'
import { payTypeLabel, roleLabel } from './format'

export const payrollStatusLabel: Record<PayrollStatus, string> = {
  DRAFT: 'Nháp',
  FINALIZED: 'Đã chốt',
}

/** "2026-03" → "03/2026". */
export function periodLabel(period: string): string {
  const [year, month] = period.split('-')
  return `${month}/${year}`
}

/** A payroll as a tab-separated sheet for Excel (FR-15.5): money as plain numbers, with a total row. */
export function payrollSheet(payroll: PayrollDetail): string {
  const sum = (pick: (s: PayrollDetail['payslips'][number]) => number) => payroll.payslips.reduce((total, s) => total + pick(s), 0)
  const rows: (string | number)[][] = [
    ['Nhân viên', 'Vai trò', 'Hình thức', 'Mức lương', 'Số phút làm', 'Ngày công', 'Ngày nghỉ có lương', 'Lương theo công', 'Thưởng trừ phạt', 'Thực nhận'],
    ...payroll.payslips.map((s) => [
      s.employeeName,
      roleLabel[s.role],
      payTypeLabel[s.payType],
      s.payRate,
      s.workedMinutes,
      s.workDays,
      s.paidLeaveDays,
      s.baseAmount,
      s.adjustmentAmount,
      s.netAmount,
    ]),
    ['Tổng', '', '', '', '', '', '', sum((s) => s.baseAmount), sum((s) => s.adjustmentAmount), sum((s) => s.netAmount)],
  ]
  return rows.map((row) => row.map((cell) => String(cell).replace(/[\t\r\n]+/g, ' ')).join('\t')).join('\r\n')
}

/** UTF-16LE with a byte-order mark: Excel opens it as a table whatever the Windows language. */
export function utf16leWithBom(text: string): Uint8Array<ArrayBuffer> {
  const bytes = new Uint8Array(2 + text.length * 2)
  bytes[0] = 0xff
  bytes[1] = 0xfe
  for (let i = 0; i < text.length; i++) {
    const code = text.charCodeAt(i)
    bytes[2 + i * 2] = code & 0xff
    bytes[3 + i * 2] = code >> 8
  }
  return bytes
}

export const leaveTypeLabel: Record<LeaveType, string> = {
  PAID: 'Có lương',
  UNPAID: 'Không lương',
}

export const leaveStatusLabel: Record<LeaveStatus, string> = {
  PENDING: 'Chờ duyệt',
  APPROVED: 'Đã duyệt',
  REJECTED: 'Từ chối',
  CANCELLED: 'Đã huỷ',
}

export const leaveStatusColor: Record<LeaveStatus, string> = {
  PENDING: 'gold',
  APPROVED: 'green',
  REJECTED: 'red',
  CANCELLED: 'default',
}

/** "15/05 – 16/05 (2 ngày)" or "15/05 (1 ngày)". */
export function leaveDays(leave: Pick<LeaveRequest, 'fromDate' | 'toDate' | 'days'>): string {
  const from = dayjs(leave.fromDate).format('DD/MM')
  const to = dayjs(leave.toDate).format('DD/MM')
  return `${from === to ? from : `${from} – ${to}`} (${leave.days} ngày)`
}

/** "07:00:00" → "07:00". */
export function hhmm(value: string | null): string {
  return value ? value.slice(0, 5) : ''
}

/** "07:30:00" as a time of the given day, for a TimePicker. */
export function timeOf(value: string, day: Dayjs = dayjs()): Dayjs {
  const [h, m] = value.split(':').map(Number)
  return day.startOf('day').hour(h).minute(m)
}

/** Monday of the week that contains the given day. */
export function mondayOf(day: Dayjs): Dayjs {
  return day.startOf('day').subtract((day.day() + 6) % 7, 'day')
}

/** The seven days starting on the given day. */
export function weekDays(first: Dayjs): Dayjs[] {
  return Array.from({ length: 7 }, (_, i) => first.add(i, 'day'))
}

/** "8 giờ", "45 phút", "1 giờ 40 phút". */
export function duration(minutes: number | null): string {
  if (minutes === null) return ''
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h === 0) return `${m} phút`
  return m === 0 ? `${h} giờ` : `${h} giờ ${m} phút`
}
