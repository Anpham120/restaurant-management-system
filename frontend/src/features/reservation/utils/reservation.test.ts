import { describe, expect, it } from 'vitest'
import { depositState } from './reservation'

describe('deposit of a booking (BR-42)', () => {
  it('is none when no deposit is asked', () => {
    expect(depositState({ depositAmount: 0, depositPaidAt: null, depositApplied: null })).toBe('NONE')
  })

  it('waits until the transfer comes in', () => {
    expect(depositState({ depositAmount: 500_000, depositPaidAt: null, depositApplied: null })).toBe('WAITING')
  })

  it('is held once received, until the bill is paid', () => {
    expect(depositState({ depositAmount: 500_000, depositPaidAt: '2026-10-01T05:00:00Z', depositApplied: null })).toBe('PAID')
    expect(depositState({ depositAmount: 500_000, depositPaidAt: '2026-10-01T05:00:00Z', depositApplied: 300_000 })).toBe('APPLIED')
  })
})
