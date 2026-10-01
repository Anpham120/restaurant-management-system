import { describe, expect, it } from 'vitest'
import type { PayrollDetail } from '@/shared/api/types'
import { payrollSheet, periodLabel } from './payroll'

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
