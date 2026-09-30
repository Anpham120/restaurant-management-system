import { describe, expect, it } from 'vitest'
import dayjs from 'dayjs'
import type { PayrollDetail } from '../api/types'
import { duration, hhmm, leaveDays, mondayOf, payrollSheet, periodLabel, timeOf, utf16leWithBom, weekDays } from './hr'

describe('periodLabel', () => {
  it('reads a period the Vietnamese way', () => {
    expect(periodLabel('2036-03')).toBe('03/2036')
  })
})

describe('payrollSheet', () => {
  it('writes one row per person and a total row, separated by tabs', () => {
    const payroll = {
      id: 1,
      period: '2036-03',
      status: 'DRAFT',
      standardDays: 26,
      payslipCount: 1,
      totalNet: 170000,
      finalizedAt: null,
      finalizedByName: null,
      payslips: [
        {
          id: 7,
          period: '2036-03',
          status: 'DRAFT',
          employeeId: 3,
          employeeName: 'Phạm Thị Ngân',
          role: 'CASHIER',
          payType: 'HOURLY',
          payRate: 30000,
          workedMinutes: 240,
          workDays: 1,
          paidLeaveDays: 0,
          baseAmount: 120000,
          adjustmentAmount: 50000,
          netAmount: 170000,
          adjustments: [],
        },
      ],
    } satisfies PayrollDetail
    const lines = payrollSheet(payroll).split('\r\n')
    expect(lines).toHaveLength(3)
    expect(lines[1]).toBe('Phạm Thị Ngân\tThu ngân\tTheo giờ\t30000\t240\t1\t0\t120000\t50000\t170000')
    expect(lines[2].split('\t').slice(-3)).toEqual(['120000', '50000', '170000'])
  })
})

describe('utf16leWithBom', () => {
  it('starts with the byte-order mark and stores each character in two bytes', () => {
    expect([...utf16leWithBom('Aă')]).toEqual([0xff, 0xfe, 0x41, 0x00, 0x03, 0x01])
  })
})

describe('leaveDays', () => {
  it('shows the range and the number of days', () => {
    expect(leaveDays({ fromDate: '2026-05-15', toDate: '2026-05-16', days: 2 })).toBe('15/05 – 16/05 (2 ngày)')
    expect(leaveDays({ fromDate: '2026-05-15', toDate: '2026-05-15', days: 1 })).toBe('15/05 (1 ngày)')
  })
})

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

describe('duration', () => {
  it('reads minutes as hours and minutes', () => {
    expect(duration(480)).toBe('8 giờ')
    expect(duration(45)).toBe('45 phút')
    expect(duration(100)).toBe('1 giờ 40 phút')
    expect(duration(0)).toBe('0 phút')
    expect(duration(null)).toBe('')
  })
})
