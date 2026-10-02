import dayjs from 'dayjs'
import type { DishProfit, ExceptionsReport, GrossProfitReport, ReportSummary, RevenueMethod } from '@/shared/api/types'
import { auditActionLabel } from '@/features/audit/utils/audit'
import { toSheet } from '@/shared/utils/sheet'

export const methodLabel: Record<RevenueMethod, string> = {
  CASH: 'Tiền mặt',
  BANK_TRANSFER: 'Chuyển khoản',
  DEPOSIT: 'Cọc',
  GRABFOOD: 'GrabFood',
  SHOPEEFOOD: 'ShopeeFood',
}

/** BR-40: gross profit as a whole percent of the dish revenue; null without a full cost. */
export function profitRate(dish: Pick<DishProfit, 'revenue' | 'grossProfit'>): number | null {
  if (dish.grossProfit === null || dish.revenue === 0) return null
  return Math.round((dish.grossProfit / dish.revenue) * 100)
}

/** FR-10.6: every day of the range, with no sales as zero, so the chart keeps its time line. */
export function everyDay(from: string, to: string, byDay: ReportSummary['byDay']): ReportSummary['byDay'] {
  const sales = new Map(byDay.map((d) => [d.date, d]))
  const days: ReportSummary['byDay'] = []
  for (let day = dayjs(from); !day.isAfter(dayjs(to), 'day'); day = day.add(1, 'day')) {
    const date = day.format('YYYY-MM-DD')
    days.push(sales.get(date) ?? { date, amount: 0, count: 0 })
  }
  return days
}

/** FR-10.7: the report on screen as one sheet for Excel, a table under each heading; money as plain numbers. */
export function reportSheet(summary: ReportSummary, profit: GrossProfitReport, exceptions: ExceptionsReport): string {
  const date = (value: string) => dayjs(value).format('DD/MM/YYYY')
  return toSheet([
    [`Báo cáo từ ${date(summary.from)} đến ${date(summary.to)}`],
    [],
    ['Doanh thu', summary.revenue],
    ['Số đơn đã thanh toán', summary.orderCount],
    ['Trung bình mỗi đơn', summary.averagePerOrder],
    ['Giảm giá, tặng món', profit.discounts],
    ['Doanh thu món đủ giá vốn', profit.costedRevenue],
    ['Giá vốn', profit.cost],
    ['Lãi gộp', profit.grossProfit],
    [],
    ['Doanh thu theo ngày'],
    ['Ngày', 'Số đơn', 'Doanh thu'],
    ...summary.byDay.map((d) => [date(d.date), d.count, d.amount]),
    [],
    ['Theo phương thức'],
    ['Phương thức', 'Số đơn', 'Doanh thu'],
    ...summary.byMethod.map((m) => [methodLabel[m.method], m.count, m.amount]),
    [],
    ['Top 10 món bán chạy'],
    ['Món', 'Số lượng', 'Doanh thu'],
    ...summary.topItems.map((t) => [t.itemName, t.quantity, t.amount]),
    [],
    ['Lãi gộp theo món'],
    ['Món', 'Số lượng', 'Doanh thu', 'Giá vốn', 'Lãi gộp', 'Tỷ lệ lãi gộp (%)'],
    ...profit.dishes.map((d) => [d.itemName, d.quantity, d.revenue, d.cost ?? 'Chưa đủ', d.grossProfit ?? '', profitRate(d) ?? '']),
    [],
    ['Ngoại lệ'],
    ['Loại', 'Số lần', 'Số tiền'],
    ...exceptions.byAction.map((a) => [auditActionLabel[a.action], a.count, a.amount]),
    [],
    ['Ngoại lệ theo người'],
    ['Người làm', 'Loại', 'Số lần', 'Số tiền'],
    ...exceptions.byPerson.map((p) => [p.employeeName, auditActionLabel[p.action], p.count, p.amount]),
  ])
}
