import { money } from '@/shared/utils/format'

/** FR-17.1: the float a shift usually starts with. */
export const OPENING_FLOAT = 1_000_000

/** BR-39: paying out more than this needs a manager. */
export const EXPENSE_LIMIT = 300_000

/** BR-39: the count against what the drawer should hold. */
export function differenceText(difference: number): string {
  if (difference === 0) return 'Khớp'
  return `${difference > 0 ? 'Thừa' : 'Thiếu'} ${money(Math.abs(difference))}`
}
