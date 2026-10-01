import type { Order, OrderItem } from '@/shared/api/types'

export function orderTitle(o: Order) {
  return o.type === 'TAKEAWAY' ? `Mang về #${o.id}` : `Bàn ${o.tableName}`
}

/** BR-12, BR-33: what a bill charges for, confirmed dishes that were not cancelled, with the amount of each line. */
export function billLines(items: OrderItem[]) {
  return items
    .filter((i) => i.status !== 'PENDING' && i.status !== 'CANCELLED')
    .map((i) => ({ ...i, amount: i.unitPrice * i.quantity }))
}
