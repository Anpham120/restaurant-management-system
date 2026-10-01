import { describe, expect, it } from 'vitest'
import type { ExceptionsReport, GrossProfitReport, ReportSummary } from '@/shared/api/types'
import { everyDay, profitRate, reportSheet } from './report'

const summary: ReportSummary = {
  from: '2026-10-01',
  to: '2026-10-03',
  revenue: 120_000,
  orderCount: 1,
  averagePerOrder: 120_000,
  byMethod: [{ method: 'CASH', amount: 120_000, count: 1 }],
  byDay: [{ date: '2026-10-02', amount: 120_000, count: 1 }],
  topItems: [{ itemName: 'Phở bò', quantity: 2, amount: 130_000 }],
}

const profit: GrossProfitReport = {
  from: '2026-10-01',
  to: '2026-10-03',
  dishRevenue: 140_000,
  revenue: 120_000,
  discounts: 20_000,
  costedRevenue: 130_000,
  cost: 60_000,
  grossProfit: 70_000,
  dishes: [
    { itemName: 'Phở bò', quantity: 2, revenue: 130_000, cost: 60_000, grossProfit: 70_000 },
    { itemName: 'Trà đá', quantity: 2, revenue: 10_000, cost: null, grossProfit: null },
  ],
}

const exceptions: ExceptionsReport = {
  from: '2026-10-01',
  to: '2026-10-03',
  byAction: [{ action: 'ITEM_CANCELLED', count: 2, amount: 90_000 }],
  byPerson: [{ employeeId: 7, employeeName: 'Lê Văn Phục', action: 'ITEM_CANCELLED', count: 2, amount: 90_000 }],
}

describe('gross profit rate (BR-40)', () => {
  it('is the profit over the dish revenue, in whole percent', () => {
    expect(profitRate({ revenue: 130_000, grossProfit: 70_000 })).toBe(54)
  })

  it('has none without a full cost or without revenue', () => {
    expect(profitRate({ revenue: 10_000, grossProfit: null })).toBeNull()
    expect(profitRate({ revenue: 0, grossProfit: 0 })).toBeNull()
  })
})

describe('days of the chart (FR-10.6)', () => {
  it('keeps every day of the range, a day with no sales as zero', () => {
    expect(everyDay(summary.from, summary.to, summary.byDay)).toEqual([
      { date: '2026-10-01', amount: 0, count: 0 },
      { date: '2026-10-02', amount: 120_000, count: 1 },
      { date: '2026-10-03', amount: 0, count: 0 },
    ])
  })
})

describe('the report for Excel (FR-10.7)', () => {
  const lines = reportSheet(summary, profit, exceptions).split('\r\n')

  it('opens with the range and the totals', () => {
    expect(lines[0]).toBe('Báo cáo từ 01/10/2026 đến 03/10/2026')
    expect(lines).toContain('Doanh thu\t120000')
    expect(lines).toContain('Giảm giá, tặng món\t20000')
  })

  it('writes a dish without a full cost as such', () => {
    expect(lines).toContain('Phở bò\t2\t130000\t60000\t70000\t54')
    expect(lines).toContain('Trà đá\t2\t10000\tChưa đủ\t\t')
  })

  it('names the exceptions in words', () => {
    expect(lines).toContain('Lê Văn Phục\tHuỷ món\t2\t90000')
    expect(lines).toContain('02/10/2026\t1\t120000')
    expect(lines).toContain('Tiền mặt\t1\t120000')
  })
})
