import type { Reservation, ReservationStatus } from '@/shared/api/types'

export const statusLabel: Record<ReservationStatus, string> = {
  BOOKED: 'Chờ khách',
  SEATED: 'Đã nhận khách',
  CANCELLED: 'Đã huỷ',
  NO_SHOW: 'Không tới',
}

export const statusColor: Record<ReservationStatus, string> = {
  BOOKED: 'blue',
  SEATED: 'green',
  CANCELLED: 'default',
  NO_SHOW: 'red',
}

export type DepositState = 'NONE' | 'WAITING' | 'PAID' | 'APPLIED'

/** BR-42: where the deposit of a booking stands. */
export function depositState(r: Pick<Reservation, 'depositAmount' | 'depositPaidAt' | 'depositApplied'>): DepositState {
  if (r.depositAmount === 0) return 'NONE'
  if (!r.depositPaidAt) return 'WAITING'
  return r.depositApplied === null ? 'PAID' : 'APPLIED'
}

export const depositLabel: Record<DepositState, string> = {
  NONE: 'Không cọc',
  WAITING: 'Chờ cọc',
  PAID: 'Đã nhận cọc',
  APPLIED: 'Đã trừ vào bill',
}

export const depositColor: Record<DepositState, string> = {
  NONE: 'default',
  WAITING: 'gold',
  PAID: 'green',
  APPLIED: 'default',
}
