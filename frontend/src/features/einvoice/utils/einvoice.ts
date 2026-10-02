import type { EInvoiceStatus } from '@/shared/api/types'

export const statusLabel: Record<EInvoiceStatus, string> = {
  PENDING: 'Chờ xuất',
  EXPORTED: 'Đã xuất file',
  ISSUED: 'Đã có số',
}

export const statusColor: Record<EInvoiceStatus, string> = {
  PENDING: 'gold',
  EXPORTED: 'blue',
  ISSUED: 'green',
}

/** BR-46: 10 digits, 10 digits and a 3-digit branch (0101234567-001), or 12 digits. */
export function isTaxCode(value: string): boolean {
  return /^(\d{10}(-\d{3})?|\d{12})$/.test(value.trim())
}

/** BR-46: the symbol of an issued invoice, 7 characters such as 1C26MKB. */
export function isSymbol(value: string): boolean {
  return /^[1-9][CK]\d{2}[A-Z][A-Z0-9]{2}$/.test(value.trim().toUpperCase())
}

/** BR-46: 1 to 8 digits; leading zeros are dropped. */
export function isInvoiceNo(value: string): boolean {
  return /^0*[1-9]\d{0,7}$/.test(value.trim())
}
