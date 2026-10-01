import { describe, expect, it } from 'vitest'
import { consentState, normalizePhone } from './customer'

describe('phone number of a guest (BR-44)', () => {
  it('keeps the digits, so spaces and dots do not make another guest', () => {
    expect(normalizePhone('0912 345 678')).toBe('0912345678')
    expect(normalizePhone('0912.345.678')).toBe('0912345678')
  })

  it('reads +84 or 84 in front as 0', () => {
    expect(normalizePhone('+84 912 345 678')).toBe('0912345678')
    expect(normalizePhone('84912345678')).toBe('0912345678')
  })

  it('takes only ten digits from 0', () => {
    expect(normalizePhone('091234567')).toBeNull()
    expect(normalizePhone('09123456789')).toBeNull()
    expect(normalizePhone('1912345678')).toBeNull()
    expect(normalizePhone('+8491234567')).toBeNull()
    expect(normalizePhone('')).toBeNull()
  })
})

describe('messages to a guest (BR-44)', () => {
  it('are not sent until the guest agrees', () => {
    expect(consentState({ mayContact: false, optedOutAt: null })).toBe('NONE')
    expect(consentState({ mayContact: true, optedOutAt: null })).toBe('AGREED')
  })

  it('stop when the guest refuses', () => {
    expect(consentState({ mayContact: false, optedOutAt: '2026-10-01T05:00:00Z' })).toBe('REFUSED')
  })
})
