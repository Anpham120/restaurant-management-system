import { describe, expect, it } from 'vitest'
import dayjs from 'dayjs'
import { hhmm, mondayOf, timeOf, weekDays } from './schedule'

describe('hhmm', () => {
  it('drops the seconds', () => {
    expect(hhmm('07:00:00')).toBe('07:00')
    expect(hhmm(null)).toBe('')
  })
})

describe('timeOf', () => {
  it('puts the time on the given day', () => {
    expect(timeOf('07:30:00', dayjs('2026-10-05T15:45:00')).format('YYYY-MM-DD HH:mm')).toBe('2026-10-05 07:30')
  })
})

describe('mondayOf', () => {
  it('goes back to Monday, also from a Sunday', () => {
    expect(mondayOf(dayjs('2026-10-07')).format('YYYY-MM-DD')).toBe('2026-10-05') // Wednesday
    expect(mondayOf(dayjs('2026-10-11')).format('YYYY-MM-DD')).toBe('2026-10-05') // Sunday
    expect(mondayOf(dayjs('2026-10-05')).format('YYYY-MM-DD')).toBe('2026-10-05') // Monday
  })
})

describe('weekDays', () => {
  it('lists seven days in a row', () => {
    const days = weekDays(dayjs('2026-10-05')).map((d) => d.format('DD/MM'))
    expect(days).toEqual(['05/10', '06/10', '07/10', '08/10', '09/10', '10/10', '11/10'])
  })
})
