import type { ConsentChannel, Customer } from '@/shared/api/types'

/** BR-44, as the backend does it: digits only, +84 or 84 in front becomes 0; null unless ten digits from 0. */
export function normalizePhone(raw: string): string | null {
  let digits = raw.replace(/\D/g, '')
  if (digits.length === 11 && digits.startsWith('84')) digits = '0' + digits.slice(2)
  return /^0\d{9}$/.test(digits) ? digits : null
}

export type ConsentState = 'NONE' | 'AGREED' | 'REFUSED'

/** BR-44: the guest may be sent messages when they agreed and have not refused since. */
export function consentState(c: Pick<Customer, 'mayContact' | 'optedOutAt'>): ConsentState {
  if (c.mayContact) return 'AGREED'
  return c.optedOutAt ? 'REFUSED' : 'NONE'
}

export const consentLabel: Record<ConsentState, string> = {
  NONE: 'Chưa hỏi nhận tin',
  AGREED: 'Đồng ý nhận tin',
  REFUSED: 'Từ chối nhận tin',
}

export const consentColor: Record<ConsentState, string> = {
  NONE: 'default',
  AGREED: 'green',
  REFUSED: 'red',
}

export const channelLabel: Record<ConsentChannel, string> = {
  ZALO: 'Zalo',
  SMS: 'SMS',
}
