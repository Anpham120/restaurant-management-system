import { useQuery } from '@tanstack/react-query';

import { api } from '../../api/client';
import type { HandoverRow, Ingredient, KitchenLot, KitchenType, RawLot } from '../../api/types';
import { StatusTag } from '../../components/ui';
import { docStatusLabel } from '../../lib/format';

export const useIngredients = (activeOnly = true) =>
  useQuery({ queryKey: ['ingredients', activeOnly], queryFn: () => api.get<Ingredient[]>(`/api/ingredients?activeOnly=${activeOnly}`) });

export const useKitchenTypes = (activeOnly = true) =>
  useQuery({ queryKey: ['kitchen-types', activeOnly ? 'active' : 'all'], queryFn: () => api.get<KitchenType[]>(`/api/kitchen-ingredient-types?activeOnly=${activeOnly}`) });

/** Lô RAW còn hàng, theo thứ tự FEFO (chỉ Manager). */
export const useRawLots = (enabled = true) =>
  useQuery({ queryKey: ['raw-lots'], queryFn: () => api.get<RawLot[]>('/api/inventory/raw-lots'), enabled });

export const useKitchenLots = () => useQuery({ queryKey: ['kitchen-lots'], queryFn: () => api.get<KitchenLot[]>('/api/inventory/kitchen-lots') });

export const useHandover = () => useQuery({ queryKey: ['handover'], queryFn: () => api.get<HandoverRow[]>('/api/inventory/handover') });

/** Truy vấn cần làm mới sau mọi thay đổi số dư kho. */
export const STOCK_KEYS = [['raw-lots'], ['kitchen-lots'], ['handover'], ['raw-stock'], ['kitchen-stock'], ['unusable-lots']];

export function DocStatus({ status }: { status: string }) {
  const tone = status === 'DRAFT' || status === 'ISSUED' ? 'wait' : status === 'CANCELLED' ? 'stop' : 'go';
  return <StatusTag tone={tone}>{docStatusLabel[status] ?? status}</StatusTag>;
}
