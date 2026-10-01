import type { Order } from '@/shared/api/types'

/**
 * BR-43: what is left to pay, shared by this many people. Each part is the whole-đồng share and the đồng left over
 * go one by one to the first parts, so the parts add up to the amount exactly.
 */
export function splitEvenly(amount: number, people: number): number[] {
  const share = Math.floor(amount / people)
  const left = amount - share * people
  return Array.from({ length: people }, (_, i) => share + (i < left ? 1 : 0))
}

/**
 * BR-43: the part of the dishes picked, with the discounts and the deposit shared in proportion to the dishes, rounded
 * down to the đồng and never more than what is left. Picking every dish pays the whole rest.
 */
export function shareOfDishes(picked: number, order: Pick<Order, 'subtotal' | 'total' | 'due'>): number {
  if (order.subtotal <= 0 || picked >= order.subtotal) return order.due
  return Math.min(order.due, Math.floor((picked * order.total) / order.subtotal))
}
