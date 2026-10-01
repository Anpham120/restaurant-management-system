import { describe, expect, it } from 'vitest'
import { shareOfDishes, splitEvenly } from './split'

describe('an even split (BR-43)', () => {
  it('gives the đồng left over to the first parts, so the parts add up exactly', () => {
    const parts = splitEvenly(1_000_001, 3)
    expect(parts).toEqual([333_334, 333_334, 333_333])
    expect(parts.reduce((a, b) => a + b, 0)).toBe(1_000_001)
  })

  it('splits a round amount into equal parts', () => {
    expect(splitEvenly(900_000, 3)).toEqual([300_000, 300_000, 300_000])
  })
})

describe('a split by dishes (BR-43)', () => {
  it('shares the discount in proportion to the dishes picked', () => {
    expect(shareOfDishes(200_000, { subtotal: 600_000, total: 540_000, due: 540_000 })).toBe(180_000)
  })

  it('rounds down and never asks for more than what is left', () => {
    expect(shareOfDishes(100_000, { subtotal: 300_000, total: 200_000, due: 200_000 })).toBe(66_666)
    expect(shareOfDishes(500_000, { subtotal: 600_000, total: 540_000, due: 100_000 })).toBe(100_000)
  })

  it('takes the whole rest when every dish is picked', () => {
    expect(shareOfDishes(600_000, { subtotal: 600_000, total: 540_000, due: 233_333 })).toBe(233_333)
  })
})
