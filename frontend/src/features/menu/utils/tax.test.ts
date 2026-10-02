import { describe, expect, it } from 'vitest'
import { upcomingRates } from './tax'

describe('tax rates by day (BR-45)', () => {
  const rates = [
    { rate: 10, effectiveFrom: '2027-01-01' },
    { rate: 8, effectiveFrom: '2025-07-01' },
    { rate: 5, effectiveFrom: '2026-10-02' },
  ]

  it('shows the changes still to come, the soonest first', () => {
    expect(upcomingRates(rates, '2026-10-01')).toEqual([
      { rate: 5, effectiveFrom: '2026-10-02' },
      { rate: 10, effectiveFrom: '2027-01-01' },
    ])
  })

  it('leaves out the rate starting today, which is already in force', () => {
    expect(upcomingRates(rates, '2026-10-02')).toEqual([{ rate: 10, effectiveFrom: '2027-01-01' }])
  })
})
