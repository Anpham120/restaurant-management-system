import { describe, expect, it } from 'vitest'
import { duration } from './attendance'

describe('duration', () => {
  it('reads minutes as hours and minutes', () => {
    expect(duration(480)).toBe('8 giờ')
    expect(duration(45)).toBe('45 phút')
    expect(duration(100)).toBe('1 giờ 40 phút')
    expect(duration(0)).toBe('0 phút')
    expect(duration(null)).toBe('')
  })
})
