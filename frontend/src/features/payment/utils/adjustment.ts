import type { Adjustment, AdjustmentReason, AdjustmentStatus, Order } from '@/shared/api/types'

/** BR-35: past either limit a cashier needs a manager. Same numbers as AdjustmentService on the server. */
export const LIMIT_PERCENT = 10
export const LIMIT_AMOUNT = 150_000

export const adjustmentReasonLabel: Record<AdjustmentReason, string> = {
  WAIT: 'Chờ lâu',
  FOOD_QUALITY: 'Lỗi món',
  STAFF_ERROR: 'Lỗi nhân viên',
  PROMOTION: 'Khuyến mãi',
  OTHER: 'Khác',
}

export const adjustmentStatusLabel: Record<AdjustmentStatus, string> = {
  PENDING: 'Chờ duyệt',
  APPLIED: 'Có hiệu lực',
  REJECTED: 'Bị từ chối',
  CANCELLED: 'Đã huỷ',
}

export const adjustmentStatusColor: Record<AdjustmentStatus, string> = {
  PENDING: 'gold',
  APPLIED: 'green',
  REJECTED: 'red',
  CANCELLED: 'default',
}

export function adjustmentLabel(a: Adjustment): string {
  return a.type === 'COMP' ? `Tặng ${a.itemName}` : 'Giảm giá bill'
}

export function adjustmentReason(a: Adjustment): string {
  return a.note ? `${adjustmentReasonLabel[a.reason]}: ${a.note}` : adjustmentReasonLabel[a.reason]
}

/** In effect, or waiting for a manager: both count toward the limit of the bill. */
export function isOpen(a: Adjustment): boolean {
  return a.status === 'PENDING' || a.status === 'APPLIED'
}

/**
 * BR-35, as the server will decide it: more than the dishes are worth, waiting for a manager, or in effect at once.
 */
export function adjustmentOutcome(order: Order, amount: number, manager: boolean): 'over' | 'approval' | 'now' {
  const open = order.adjustments.filter(isOpen).reduce((sum, a) => sum + a.amount, 0) + amount
  if (open > order.subtotal) return 'over'
  if (!manager && (open * 100 > order.subtotal * LIMIT_PERCENT || open > LIMIT_AMOUNT)) return 'approval'
  return 'now'
}
