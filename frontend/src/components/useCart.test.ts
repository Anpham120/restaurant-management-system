import { describe, expect, it } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import type { MenuItem } from '../api/types'
import { useCart } from './useCart'

const NEM: MenuItem = {
  id: 1,
  categoryId: 1,
  categoryName: 'Món chính',
  name: 'Nem rán',
  price: 65_000,
  description: null,
  available: true,
}

describe('useCart', () => {
  it('stops at 50 of one dish, as the backend does (BR-06)', () => {
    const { result } = renderHook(() => useCart())
    act(() => {
      for (let i = 0; i < 51; i++) result.current.add(NEM)
    })
    expect(result.current.quantities[NEM.id]).toBe(50)
    expect(result.current.total).toBe(50 * 65_000)
  })

  it('empties after clear', () => {
    const { result } = renderHook(() => useCart())
    act(() => result.current.add(NEM))
    act(() => result.current.clear())
    expect(result.current.count).toBe(0)
    expect(result.current.toItemLines()).toEqual([])
  })
})
