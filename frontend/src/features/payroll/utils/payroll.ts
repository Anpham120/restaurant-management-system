import type { PayrollDetail, PayrollStatus } from '@/shared/api/types'
import { payTypeLabel, roleLabel } from '@/shared/utils/format'

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
  return rows.map((row) => row.map((cell) => String(cell).replace(/[\t\r\n]+/g, ' ')).join('\t')).join('\r\n')
}

/** UTF-16LE with a byte-order mark: Excel opens it as a table whatever the Windows language. */
export function utf16leWithBom(text: string): Uint8Array<ArrayBuffer> {
  const bytes = new Uint8Array(2 + text.length * 2)
  bytes[0] = 0xff
  bytes[1] = 0xfe
  for (let i = 0; i < text.length; i++) {
    const code = text.charCodeAt(i)
    bytes[2 + i * 2] = code & 0xff
    bytes[3 + i * 2] = code >> 8
  }
  return bytes
}
