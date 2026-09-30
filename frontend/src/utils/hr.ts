import dayjs, { type Dayjs } from 'dayjs'
import type { LeaveRequest, LeaveStatus, LeaveType } from '../api/types'

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
