import { api } from './client';
import type { OrderItem } from './types';

export const readyOrdersQuery = {
  queryKey: ['ready-order-items'],
  queryFn: () => api.get<OrderItem[]>('/api/order-items/ready'),
};
