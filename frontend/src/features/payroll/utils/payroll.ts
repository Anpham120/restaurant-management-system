import type { PayrollDetail, PayrollStatus } from '@/shared/api/types'
import { payTypeLabel, roleLabel } from '@/shared/utils/format'
import { toSheet } from '@/shared/utils/sheet'

export const payrollStatusLabel: Record<PayrollStatus, string> = {
  DRAFT: 'Nháp',
  FINALIZED: 'Đã chốt',
}

/** "2026-03" → "03/2026". */
export function periodLabel(period: string): string {
  const [year, month] = period.split('-')
  return `${month}/${year}`
}

/** A payroll as a tab-separated sheet for Excel (FR-15.5): money as plain numbers, with a total row. */
export function payrollSheet(payroll: PayrollDetail): string {
  const sum = (pick: (s: PayrollDetail['payslips'][number]) => number) => payroll.payslips.reduce((total, s) => total + pick(s), 0)
  const rows: (string | number)[][] = [
    ['Nhân viên', 'Vai trò', 'Hình thức', 'Mức lương', 'Số phút làm', 'Ngày công', 'Ngày nghỉ có lương', 'Lương theo công', 'Thưởng trừ phạt', 'Thực nhận'],
    ...payroll.payslips.map((s) => [
      s.employeeName,
      roleLabel[s.role],
      payTypeLabel[s.payType],
      s.payRate,
      s.workedMinutes,
      s.workDays,
      s.paidLeaveDays,
      s.baseAmount,
      s.adjustmentAmount,
      s.netAmount,
    ]),
    ['Tổng', '', '', '', '', '', '', sum((s) => s.baseAmount), sum((s) => s.adjustmentAmount), sum((s) => s.netAmount)],
  ]
  return toSheet(rows)
}
