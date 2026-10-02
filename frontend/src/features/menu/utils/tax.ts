import type { TaxCategory } from '@/shared/api/types'

/** BR-45: the VAT rates of Vietnamese law, the cut rate of 8% included. */
export const VAT_RATES = [0, 5, 8, 10]

/** BR-45: the rates set to start after today, the soonest first; days are ISO dates in Vietnam. */
export function upcomingRates(rates: TaxCategory['rates'], today: string): TaxCategory['rates'] {
  return rates.filter((r) => r.effectiveFrom > today).sort((a, b) => a.effectiveFrom.localeCompare(b.effectiveFrom))
}
