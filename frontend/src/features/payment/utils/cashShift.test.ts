import { describe, expect, it } from 'vitest'
import { differenceText } from './cashShift'

describe('cash count difference (FR-17.3)', () => {
  it('calls a count below the expected cash short', () => {
    expect(differenceText(-10_000)).toBe('Thiếu 10.000 đ')
  })

  it('calls a count above the expected cash over', () => {
    expect(differenceText(5_000)).toBe('Thừa 5.000 đ')
  })

  it('says it matches when there is no difference', () => {
    expect(differenceText(0)).toBe('Khớp')
  })
})
