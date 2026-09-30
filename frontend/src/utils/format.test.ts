import { describe, expect, it } from 'vitest'
import { cashSuggestions, hasRole, homePath, minutesSince, money } from './format'

describe('money', () => {
  it('formats VND with thousands separators', () => {
    expect(money(245000)).toBe('245.000 đ')
    expect(money(0)).toBe('0 đ')
  })
})

describe('hasRole', () => {
  it('follows the backend hierarchy', () => {
    expect(hasRole('ADMIN', 'CHEF')).toBe(true)
    expect(hasRole('MANAGER', 'CASHIER')).toBe(true)
    expect(hasRole('MANAGER', 'ADMIN')).toBe(false)
    expect(hasRole('WAITER', 'CHEF')).toBe(false)
    expect(hasRole(undefined, 'WAITER')).toBe(false)
  })
})

describe('homePath', () => {
  it('sends each role to its own screen', () => {
    expect(homePath('WAITER')).toBe('/tables')
    expect(homePath('CHEF')).toBe('/kitchen')
    expect(homePath('CASHIER')).toBe('/cashier')
    expect(homePath('ADMIN')).toBe('/admin/reports')
  })
})

describe('minutesSince', () => {
  it('counts whole minutes and never goes negative', () => {
    const now = Date.parse('2026-09-30T12:10:30Z')
    expect(minutesSince('2026-09-30T12:00:00Z', now)).toBe(10)
    expect(minutesSince('2026-09-30T12:20:00Z', now)).toBe(0)
    expect(minutesSince(null, now)).toBe(0)
  })
})

describe('cashSuggestions', () => {
  it('offers the exact amount and rounded notes', () => {
    expect(cashSuggestions(245000)).toEqual([245000, 250000, 300000, 500000])
    expect(cashSuggestions(100000)).toEqual([100000, 500000])
  })
})
