import { describe, expect, it } from 'vitest'
import { lineTotal, receiptTotal, stockValue } from './receipt'

describe('goods receipt totals (FR-09.6, BR-37)', () => {
  it('multiplies the quantity by the unit price', () => {
    expect(lineTotal(10, 120_000)).toBe(1_200_000)
    expect(lineTotal(2.5, 45_000)).toBe(112_500)
  })

  it('rounds half a đồng up, like the backend', () => {
    expect(lineTotal(0.125, 4)).toBe(1)
    expect(lineTotal(1.005, 1_000)).toBe(1_005)
    expect(lineTotal(0.333, 10)).toBe(3)
  })

  it('counts a line still being filled in as zero', () => {
    expect(lineTotal(undefined, 120_000)).toBe(0)
    expect(lineTotal(3, null)).toBe(0)
    expect(receiptTotal([{ quantity: 10, unitPrice: 120_000 }, { quantity: 2 }, undefined, { quantity: 0.5, unitPrice: 30_000 }])).toBe(
      1_215_000,
    )
    expect(receiptTotal(undefined)).toBe(0)
  })
})

describe('stock value (FR-09.7)', () => {
  it('values the stock at its unit cost', () => {
    expect(stockValue({ quantity: 22, unitCost: 114_545 })).toBe(2_519_990)
  })

  it('has no value before the first receipt', () => {
    expect(stockValue({ quantity: 5, unitCost: null })).toBeNull()
  })
})
