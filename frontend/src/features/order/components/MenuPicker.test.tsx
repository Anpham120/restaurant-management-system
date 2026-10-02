import { describe, expect, it } from 'vitest'
import type { MenuItem } from '@/shared/api/types'
import { toSections } from './MenuPicker'

const dish = (id: number, categoryId: number, categoryName: string): MenuItem => ({
  id,
  categoryId,
  categoryName,
  name: `Món ${id}`,
  price: 10_000,
  description: null,
  available: true,
  taxCategoryId: 1,
  taxCategoryName: 'Ăn uống',
  appPrices: {},
})

describe('toSections', () => {
  it('groups the staff menu by category, keeping the order of the list', () => {
    const sections = toSections([dish(1, 2, 'Lẩu'), dish(2, 1, 'Khai vị'), dish(3, 2, 'Lẩu')])
    expect(sections.map((s) => [s.categoryName, s.items.map((i) => i.id)])).toEqual([
      ['Lẩu', [1, 3]],
      ['Khai vị', [2]],
    ])
  })
})
