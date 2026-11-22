import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router';

import { api } from '../../api/client';
import { readyOrdersQuery } from '../../api/readyOrders';
import type { OrderItem } from '../../api/types';
import { ErrorNotice } from '../../components/ErrorNotice';
import { useToast } from '../../components/Toast';
import { PageHead } from '../../components/ui';

export function ReadyOrdersPage() {
  const ready = useQuery(readyOrdersQuery);
  const queryClient = useQueryClient();
  const toast = useToast();
  const serve = useMutation({
    mutationFn: (item: OrderItem) => api.post<OrderItem>(`/api/order-items/${item.id}/serve`),
    onSuccess: () => toast('Đã xác nhận mang món ra'),
    onSettled: (_item, _error, original) => {
      queryClient.invalidateQueries({ queryKey: readyOrdersQuery.queryKey });
      queryClient.invalidateQueries({ queryKey: ['order', original.orderId] });
      queryClient.invalidateQueries({ queryKey: ['kitchen-queue'] });
    },
  });

  const byOrder = new Map<number, OrderItem[]>();
  for (const item of ready.data ?? []) {
    const items = byOrder.get(item.orderId);
    if (items) items.push(item);
    else byOrder.set(item.orderId, [item]);
  }

  return (
    <main className="main">
      <PageHead title="Món chờ mang ra"
                actions={<Link to="/tables" className="btn">Về sơ đồ bàn</Link>} />
      <ErrorNotice error={ready.error ?? serve.error} />
      {ready.isPending && <p className="muted" role="status">Đang tải món đã xong…</p>}
      {ready.data && <p className="muted" role="status">
        {ready.data.length > 0
          ? `${ready.data.length} món đã xong ở ${byOrder.size} bàn.`
          : 'Hiện chưa có món chờ mang ra.'}
      </p>}

      <div className="stack">
        {[...byOrder.entries()].map(([orderId, items]) => (
          <section className="panel stack" key={orderId} aria-labelledby={`ready-table-${orderId}`}>
            <div className="row-between">
              <h2 id={`ready-table-${orderId}`}>Bàn {items[0].tableCode}</h2>
              <Link to={`/orders/${orderId}#ticket`} className="btn btn-sm">Xem phiếu bàn</Link>
            </div>
            <ul className="panel-items">
              {items.map((item) => (
                <li key={item.id}>
                  <span className="num qty">{item.quantity}×</span>
                  <span className="what">{item.itemName}{item.note && <small className="muted"> · {item.note}</small>}</span>
                  <button type="button" className="btn btn-sm btn-primary" disabled={serve.isPending}
                          aria-label={`Đã mang ${item.itemName} ra bàn ${item.tableCode}`}
                          onClick={() => serve.mutate(item)}>
                    Đã mang ra
                  </button>
                </li>
              ))}
            </ul>
          </section>
        ))}
      </div>
    </main>
  );
}
