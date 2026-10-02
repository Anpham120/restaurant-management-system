import { describe, expect, it } from 'vitest'
import type { ItemStatus, MenuItem, OrderItem } from '@/shared/api/types'
import { forChannel, readyForShipper } from './appOrder'

const dish = (id: number, appPrices: MenuItem['appPrices']): MenuItem => ({
  id,
  categoryId: 1,
  categoryName: 'Khai vị',
  name: `Món ${id}`,
  price: 65_000,
  description: null,
  available: true,
  taxCategoryId: 1,
  taxCategoryName: 'Ăn uống',
  appPrices,
})

const item = (status: ItemStatus) => ({ status }) as OrderItem

describe('menu of an app order (BR-47)', () => {
  it('has only the dishes the app sells, at the price on the app', () => {
    const menu = [dish(1, { GRABFOOD: 79_000 }), dish(2, { SHOPEEFOOD: 81_000 }), dish(3, {})]
    expect(forChannel(menu, 'GRABFOOD').map((m) => [m.id, m.price])).toEqual([[1, 79_000]])
    expect(forChannel(menu, 'SHOPEEFOOD').map((m) => [m.id, m.price])).toEqual([[2, 81_000]])
  })
})

describe('handing an app order to the shipper (FR-21.3)', () => {
  it('waits for every dish to be done', () => {
    expect(readyForShipper({ items: [item('READY'), item('COOKING')] })).toBe(false)
    expect(readyForShipper({ items: [item('WAITING')] })).toBe(false)
    expect(readyForShipper({ items: [] })).toBe(false)
  })

  it('leaves cancelled dishes aside', () => {
    expect(readyForShipper({ items: [item('READY'), item('SERVED'), item('CANCELLED')] })).toBe(true)
    expect(readyForShipper({ items: [item('CANCELLED')] })).toBe(false)
  })
})
