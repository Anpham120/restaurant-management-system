import type { AuditAction, AuditEntry, ItemStatus } from '@/shared/api/types'
import { itemStatusLabel, money } from '@/shared/utils/format'

export const auditActionLabel: Record<AuditAction, string> = {
  ITEM_CANCELLED: 'Huỷ món',
  MANUAL_CONFIRMATION: 'Xác nhận tay',
  PRICE_CHANGED: 'Đổi giá',
  DISCOUNT_GIVEN: 'Giảm giá',
}

/** The raw before and after of a line in words: an item status, or a price. Empty when the action has none. */
export function auditChange(e: AuditEntry): string {
  if (e.beforeValue === null || e.afterValue === null) return ''
  if (e.action === 'PRICE_CHANGED') return `${money(Number(e.beforeValue))} → ${money(Number(e.afterValue))}`
  const label = (status: string) => itemStatusLabel[status as ItemStatus] ?? status
  return `${label(e.beforeValue)} → ${label(e.afterValue)}`
}

/** Which order a line is about; a menu change has none. */
export function auditOrder(e: AuditEntry): string {
  if (e.orderId === null) return ''
  return e.tableName ? `Bàn ${e.tableName} · #${e.orderId}` : `Mang về #${e.orderId}`
}
