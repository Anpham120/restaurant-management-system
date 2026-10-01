import dayjs from 'dayjs'
import type { LeaveRequest, LeaveStatus, LeaveType } from '@/shared/api/types'

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
