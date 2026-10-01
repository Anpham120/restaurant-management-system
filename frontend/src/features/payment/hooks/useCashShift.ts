import { useQuery } from '@tanstack/react-query'
import { api } from '@/shared/api/client'
import type { CashShift } from '@/shared/api/types'

/** BR-39: the open shift of the cash drawer, or null when none is open (the API answers 204). */
export function useCashShift() {
  return useQuery({
    queryKey: ['cash-shift'],
    queryFn: () => api.get<CashShift | ''>('/cash-shifts/current').then((r) => r.data || null),
  })
}
