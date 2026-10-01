import { describe, expect, it } from 'vitest'
import { leaveDays } from './leave'

describe('leaveDays', () => {
  it('shows the range and the number of days', () => {
    expect(leaveDays({ fromDate: '2026-05-15', toDate: '2026-05-16', days: 2 })).toBe('15/05 – 16/05 (2 ngày)')
    expect(leaveDays({ fromDate: '2026-05-15', toDate: '2026-05-15', days: 1 })).toBe('15/05 (1 ngày)')
  })
})
