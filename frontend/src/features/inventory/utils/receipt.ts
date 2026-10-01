import type { InventoryItem } from '@/shared/api/types'

/**
 * BR-37: quantity times the unit price, rounded to the đồng the way the backend does.
 * The quantity goes to thousandths first, so 0,125 × 4 is 0,5 and rounds up instead of drifting below.
 */
export function lineTotal(quantity: number | null | undefined, unitPrice: number | null | undefined): number {
  if (!quantity || !unitPrice) return 0
  return Math.round((Math.round(quantity * 1000) * unitPrice) / 1000)
}

/** The total of a receipt being filled in; lines still empty count as zero. */
export function receiptTotal(lines: ({ quantity?: number | null; unitPrice?: number | null } | undefined)[] | undefined): number {
  return (lines ?? []).reduce((sum, line) => sum + lineTotal(line?.quantity, line?.unitPrice), 0)
}

/** FR-09.7: what the stock on hand is worth at its unit cost; null while the item has no cost yet. */
export function stockValue(item: Pick<InventoryItem, 'quantity' | 'unitCost'>): number | null {
  return item.unitCost === null ? null : lineTotal(item.quantity, item.unitCost)
}
