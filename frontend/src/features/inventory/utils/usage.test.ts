import { describe, expect, it } from 'vitest'
import { countDifference } from './usage'

describe('stock count difference (FR-09.10)', () => {
  it('calls less than the books a shortfall', () => {
    expect(countDifference(-0.4, 'kg')).toBe('Thiếu 0.4 kg')
  })

  it('calls more than the books a surplus', () => {
    expect(countDifference(2, 'chai')).toBe('Dư 2 chai')
  })

  it('says nothing when the count agreed', () => {
    expect(countDifference(0, 'kg')).toBe('')
  })
})
