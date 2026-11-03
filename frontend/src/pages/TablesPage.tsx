import { useState } from 'react';
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router';

import { api } from '../api/client';
import type { OpenSessionResponse, OrderItem, OrderResponse, TableResponse } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { ErrorNotice } from '../components/ErrorNotice';
import { Icon } from '../components/Icon';
import { useToast } from '../components/Toast';
import { elapsed, itemStatusLabel, vnd } from '../lib/format';

type State = 'free' | 'busy' | 'bill';
const STATE_LABEL: Record<State, string> = { free: 'Trống', busy: 'Đang phục vụ', bill: 'Chờ thanh toán' };
const STATE_CHIP: Record<State, string> = { free: 'chip', busy: 'chip chip-wait', bill: 'chip chip-bill' };
const FILTERS: { key: 'all' | State; label: string }[] = [
  { key: 'all', label: 'Tất cả' },
  { key: 'free', label: 'Trống' },
  { key: 'busy', label: 'Đang phục vụ' },
  { key: 'bill', label: 'Chờ thanh toán' },
];

const DEAD = new Set(['REJECTED', 'CANCELLED']);

export function itemChip(status: string): string {
  if (status === 'READY') return 'chip chip-go';
  if (status === 'ACCEPTED' || status === 'COOKING') return 'chip chip-wait';
  if (DEAD.has(status)) return 'chip chip-stop';
  return 'chip';
}

/** Sơ đồ bàn: lọc theo trạng thái, lưới bàn và khung chi tiết bàn đang chọn (bản thiết kế "Sơ đồ bàn"). */
export function TablesPage() {
  const { hasRole } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const queryClient = useQueryClient();
  const [filter, setFilter] = useState<'all' | State>('all');
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [jump, setJump] = useState('');
  const canOpen = hasRole('WAITER', 'MANAGER');
  const canServe = hasRole('WAITER', 'MANAGER');
  const canPay = hasRole('CASHIER', 'MANAGER');

  const tables = useQuery({
    queryKey: ['tables'],
    queryFn: () => api.get<TableResponse[]>('/api/tables'),
    refetchInterval: 10_000,
  });
  const list = tables.data ?? [];

  // Phiếu của mỗi bàn đang có khách: tiến độ món, món xong chờ bưng, tạm tính
  const busy = list.filter((t) => t.currentOrderId != null);
  const orders = useQueries({
    queries: busy.map((t) => ({
      queryKey: ['order', t.currentOrderId],
      queryFn: () => api.get<OrderResponse>(`/api/orders/${t.currentOrderId}`),
      refetchInterval: 10_000,
    })),
  });
  const orderByTable = new Map<number, OrderResponse>();
  orders.forEach((q) => q.data && orderByTable.set(q.data.session.tableId, q.data));

  const stateOf = (t: TableResponse): State =>
    t.currentOrderId == null ? 'free' : orderByTable.get(t.id)?.session.invoiceStatus === 'PAYMENT_PENDING' ? 'bill' : 'busy';
  const live = (o?: OrderResponse) => (o?.items ?? []).filter((i) => !DEAD.has(i.status));
  const subtotal = (o?: OrderResponse) =>
    o?.session.invoiceTotal ?? live(o).reduce((s, i) => s + i.unitPrice * i.quantity, 0);

  const open = useMutation({
    mutationFn: (t: TableResponse) => api.post<OpenSessionResponse>(`/api/tables/${t.id}/sessions`),
    onSuccess: (res) => {
      queryClient.invalidateQueries({ queryKey: ['tables'] });
      navigate(`/orders/${res.orderId}`);
    },
  });

  const serveAll = useMutation({
    mutationFn: async (items: OrderItem[]) => {
      for (const i of items) await api.post(`/api/order-items/${i.id}/serve`);
      return items.length;
    },
    onSuccess: (n, items) => {
      toast(`Đã bưng ${n} món`);
      queryClient.invalidateQueries({ queryKey: ['order', items[0]?.orderId] });
      queryClient.invalidateQueries({ queryKey: ['kitchen-queue'] });
    },
  });

  const counts = { all: list.length, free: 0, busy: 0, bill: 0 };
  list.forEach((t) => (counts[stateOf(t)] += 1));
  const shown = list.filter((t) => filter === 'all' || stateOf(t) === filter);
  const readyAll = busy.flatMap((t) => (orderByTable.get(t.id)?.items ?? []).filter((i) => i.status === 'READY'));
  const readyTables = [...new Set(readyAll.map((i) => i.tableCode))];

  const selected = list.find((t) => t.id === selectedId) ?? null;
  const selOrder = selected ? orderByTable.get(selected.id) : undefined;
  const selState = selected ? stateOf(selected) : 'free';
  const selReady = (selOrder?.items ?? []).filter((i) => i.status === 'READY');
  const now = Date.now();

  return (
    <main className="main">
      <div className="page-head">
        <div>
          <h1>Sơ đồ bàn</h1>
          <p className="muted">
            {tables.isSuccess ? `${counts.busy + counts.bill}/${list.length} bàn đang có khách · tự cập nhật` : 'Đang tải…'}
          </p>
        </div>
        <form
          className="jump"
          onSubmit={(e) => {
            e.preventDefault();
            const t = list.find((x) => x.code.toLowerCase() === jump.trim().toLowerCase());
            if (t) {
              setSelectedId(t.id);
              setFilter('all');
              setJump('');
            } else toast(`Không có bàn ${jump.trim()}`);
          }}
        >
          <label htmlFor="tbl-jump" className="sr-only">Đi tới bàn</label>
          <input id="tbl-jump" className="input" placeholder="Gõ mã bàn, Enter" value={jump} onChange={(e) => setJump(e.target.value)} />
        </form>
      </div>

      <ErrorNotice error={tables.error ?? open.error ?? serveAll.error} />

      <div className="filter-chips" role="group" aria-label="Lọc theo trạng thái">
        {FILTERS.map((f) => (
          <button key={f.key} type="button" aria-pressed={filter === f.key} data-state={f.key} onClick={() => setFilter(f.key)}>
            <span className="dot" aria-hidden="true" />
            {f.label}
            <span className="num">{counts[f.key]}</span>
          </button>
        ))}
      </div>

      {readyAll.length > 0 && (
        <div className="notice notice-info banner">
          <Icon name="bell" />
          <span>
            <b>{readyAll.length} món đã xong, chờ bưng:</b>{' '}
            {readyTables.map((c) => `${c} (${readyAll.filter((i) => i.tableCode === c).length})`).join(' · ')}
          </span>
        </div>
      )}

      {tables.isSuccess && list.length === 0 && (
        <div className="center-screen">Chưa có bàn nào. Thêm bàn ở Cài đặt › Bàn.</div>
      )}

      <div className="floor-layout">
        <div className="floor">
          {shown.map((t) => {
            const st = stateOf(t);
            const o = orderByTable.get(t.id);
            const items = live(o);
            const done = items.filter((i) => i.status === 'SERVED' || i.status === 'READY').length;
            const ready = items.filter((i) => i.status === 'READY').length;
            return (
              <button key={t.id} type="button" className="table-tile" data-state={st} aria-pressed={t.id === selectedId}
                      aria-label={`Bàn ${t.code}, ${STATE_LABEL[st]}, ${t.capacity} chỗ`} onClick={() => setSelectedId(t.id)}>
                <span className="tile-top">
                  <span className="code">{t.code}</span>
                  <span className={STATE_CHIP[st]}>{STATE_LABEL[st]}</span>
                </span>
                {st === 'free' ? (
                  <span className="meta">{t.capacity} chỗ{canOpen ? ' · chạm để mở' : ''}</span>
                ) : (
                  <span className="meta">
                    <span>{o ? `${elapsed(o.session.openedAt, now)} · ${done}/${items.length} món` : 'Đang tải…'}</span>
                    <span className="tile-foot">
                      <span className="num total">{vnd(subtotal(o))}</span>
                      {ready > 0 && <span className="ready-badge">{ready} xong</span>}
                    </span>
                  </span>
                )}
              </button>
            );
          })}
        </div>

        <aside className="panel table-panel" aria-label="Bàn đang chọn">
          {!selected ? (
            <p className="muted">Chọn một bàn để xem phiếu, gọi thêm hoặc thanh toán.</p>
          ) : (
            <>
              <div className="row-between">
                <div>
                  <h2>Bàn {selected.code}</h2>
                  <p className="muted">
                    {selected.capacity} chỗ · {selState === 'free' ? 'sẵn sàng đón khách' : selOrder ? `ngồi ${elapsed(selOrder.session.openedAt, now)}` : '…'}
                  </p>
                </div>
                <span className={STATE_CHIP[selState]}>{STATE_LABEL[selState]}</span>
              </div>

              {selState === 'free' ? (
                <>
                  <p className="muted">Bàn trống. Mở bàn sẽ tạo phiếu gọi món mới và đưa bạn vào màn gọi món.</p>
                  {canOpen && (
                    <button type="button" className="btn btn-primary btn-lg btn-block" disabled={open.isPending}
                            onClick={() => open.mutate(selected)}>
                      {open.isPending ? 'Đang mở…' : 'Mở bàn và gọi món'}
                    </button>
                  )}
                </>
              ) : (
                <>
                  <ul className="panel-items">
                    {(selOrder?.items ?? []).length === 0 && <li className="muted">Chưa gọi món nào.</li>}
                    {(selOrder?.items ?? []).map((i) => (
                      <li key={i.id} data-dead={DEAD.has(i.status)}>
                        <span className="num qty">{i.quantity}×</span>
                        <span className="what">{i.itemName}</span>
                        <span className={itemChip(i.status)}>{itemStatusLabel[i.status] ?? i.status}</span>
                      </li>
                    ))}
                  </ul>
                  <div className="row-between subtotal">
                    <span className="muted">Tạm tính</span>
                    <span className="num">{vnd(subtotal(selOrder))}</span>
                  </div>
                  <div className="panel-actions">
                    <Link className="btn btn-primary btn-lg" to={`/orders/${selected.currentOrderId}`}>
                      {selState === 'bill' ? 'Xem phiếu' : 'Gọi thêm'}
                    </Link>
                    {canPay && (
                      <Link className="btn btn-lg" to={`/orders/${selected.currentOrderId}/invoice`}>Thanh toán</Link>
                    )}
                  </div>
                  {canServe && selReady.length > 0 && (
                    <button type="button" className="btn btn-block serve-btn" disabled={serveAll.isPending}
                            onClick={() => serveAll.mutate(selReady)}>
                      Đã bưng {selReady.length} món sẵn sàng
                    </button>
                  )}
                </>
              )}
            </>
          )}
        </aside>
      </div>
    </main>
  );
}
