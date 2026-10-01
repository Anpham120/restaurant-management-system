import { describe, expect, it } from 'vitest'
import type { Adjustment, Order } from '@/shared/api/types'
import { adjustmentLabel, adjustmentOutcome, adjustmentReason } from './adjustment'

const adjustment = (extra: Partial<Adjustment>): Adjustment => ({
  id: 1,
  orderId: 7,
  tableName: 'B05',
  type: 'DISCOUNT',
  orderItemId: null,
  itemName: null,
  amount: 20_000,
  reason: 'WAIT',
  note: null,
  status: 'APPLIED',
  createdByName: 'Phạm Thị Ngân',
  createdAt: '2026-10-01T12:00:00Z',
  decidedByName: null,
  decidedAt: null,
  ...extra,
})

const order = (subtotal: number, adjustments: Adjustment[] = []): Order => ({
  id: 7,
  type: 'DINE_IN',
  status: 'OPEN',
  tableId: 5,
  tableIds: [5],
  tableName: 'B05',
  guestCount: 2,
  note: null,
  openedAt: '2026-10-01T11:00:00Z',
  closedAt: null,
  subtotal,
  discountTotal: 0,
  depositCredit: 0,
  paidAmount: 0,
  due: 0,
  total: subtotal,
  pendingCount: 0,
  unservedCount: 0,
  pendingAdjustmentCount: 0,
  items: [],
  adjustments,
})

describe('discounts (BR-35)', () => {
  it('takes effect at once within 10% of the dishes and within 150.000', () => {
    expect(adjustmentOutcome(order(600_000), 60_000, false)).toBe('now')
  })

  it('waits for a manager past 10% or past 150.000', () => {
    expect(adjustmentOutcome(order(600_000), 61_000, false)).toBe('approval')
    expect(adjustmentOutcome(order(2_000_000), 160_000, false)).toBe('approval')
  })

  it('counts the discounts in effect or waiting, but not those rejected or cancelled', () => {
    const others = [adjustment({ amount: 40_000 }), adjustment({ amount: 15_000, status: 'PENDING' })]
    expect(adjustmentOutcome(order(600_000, others), 5_000, false)).toBe('now')
    expect(adjustmentOutcome(order(600_000, others), 6_000, false)).toBe('approval')
    const gone = [adjustment({ amount: 50_000, status: 'REJECTED' }), adjustment({ amount: 50_000, status: 'CANCELLED' })]
    expect(adjustmentOutcome(order(600_000, gone), 60_000, false)).toBe('now')
  })

  it('lets a manager past the limit but never past what the dishes are worth', () => {
    expect(adjustmentOutcome(order(600_000), 200_000, true)).toBe('now')
    expect(adjustmentOutcome(order(600_000, [adjustment({ amount: 200_000 })]), 400_001, true)).toBe('over')
  })

  it('names a discount and a dish given free, with the reason', () => {
    expect(adjustmentLabel(adjustment({}))).toBe('Giảm giá bill')
    expect(adjustmentLabel(adjustment({ type: 'COMP', itemName: 'Nem rán x2' }))).toBe('Tặng Nem rán x2')
    expect(adjustmentReason(adjustment({ reason: 'OTHER', note: 'Khách quen' }))).toBe('Khác: Khách quen')
  })
})
