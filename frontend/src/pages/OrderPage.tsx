import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router';

import { ApiError, api, newRequestKey } from '../api/client';
import type { DailyMenuItem, DailyMenuResponse, OrderItem, OrderResponse, SubmitBatchResponse } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { ErrorNotice } from '../components/ErrorNotice';
import { ReasonDialog } from '../components/ReasonDialog';
import { InvoiceStatusChip, ItemStatusChip } from '../components/StatusChip';
import { useToast } from '../components/Toast';
import { elapsed, vnd } from '../lib/format';

interface DraftLine {
  item: DailyMenuItem;
  quantity: number;
  note: string;
}

const LIVE: OrderItem['status'][] = ['PENDING', 'ACCEPTED', 'COOKING', 'READY', 'SERVED'];

/** Gọi món cho một bàn: thực đơn hôm nay bên trái, phiếu của bàn bên phải. */
export function OrderPage() {
  const orderId = Number(useParams().orderId);
  const { hasRole } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const canOrder = hasRole('WAITER', 'MANAGER');
  const canBill = hasRole('CASHIER', 'MANAGER');

  const [draft, setDraft] = useState<DraftLine[]>([]);
  // Một khóa cho một lần gửi; giữ nguyên khi thử lại để backend không tạo đợt trùng.
  const [requestKey, setRequestKey] = useState(newRequestKey);
  const [cancelling, setCancelling] = useState<OrderItem | null>(null);
  const [confirmClose, setConfirmClose] = useState(false);
  const [abandoning, setAbandoning] = useState(false);

  const order = useQuery({
    queryKey: ['order', orderId],
    queryFn: () => api.get<OrderResponse>(`/api/orders/${orderId}`),
    refetchInterval: 5_000,
  });
  const menu = useQuery({
    queryKey: ['daily-menu-today'],
    queryFn: () => api.get<DailyMenuResponse>('/api/daily-menus/today'),
    refetchInterval: 30_000,
    enabled: canOrder,
    retry: (n, e) => !(e instanceof ApiError && e.status === 404) && n < 2,
  });

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['order', orderId] });
    queryClient.invalidateQueries({ queryKey: ['daily-menu-today'] });
    queryClient.invalidateQueries({ queryKey: ['tables'] });
  };

  const submit = useMutation({
    mutationFn: () =>
      api.post<SubmitBatchResponse>(`/api/orders/${orderId}/batches`, {
        requestKey,
        items: draft.map((d) => ({ dailyMenuItemId: d.item.dailyMenuItemId, quantity: d.quantity, note: d.note.trim() || null })),
      }),
    onSuccess: (res) => {
      setDraft([]);
      setRequestKey(newRequestKey());
      toast(res.replayed ? 'Đợt này đã được gửi trước đó' : 'Đã gửi bếp');
      refresh();
    },
  });

  const itemAction = useMutation({
    mutationFn: ({ id, action, reason }: { id: number; action: string; reason?: string }) =>
      api.post(`/api/order-items/${id}/${action}`, reason ? { reason } : undefined),
    onSuccess: (_d, v) => {
      toast(v.action === 'serve' ? 'Đã mang món ra' : 'Đã hủy món');
      setCancelling(null);
      refresh();
    },
  });

  const closeEmpty = useMutation({
    mutationFn: () => api.post(`/api/sessions/${order.data!.session.sessionId}/close-empty`),
    onSuccess: () => {
      toast('Đã trả bàn');
      queryClient.invalidateQueries({ queryKey: ['tables'] });
      navigate('/tables');
    },
  });

  const abandon = useMutation({
    mutationFn: (reason: string) => api.post(`/api/sessions/${order.data!.session.sessionId}/abandon`, { reason }),
    onSuccess: () => {
      toast('Đã ghi nhận khách bỏ về');
      queryClient.invalidateQueries({ queryKey: ['tables'] });
      navigate('/tables');
    },
  });

  const groups = useMemo(() => {
    const m = new Map<string, DailyMenuItem[]>();
    for (const it of menu.data?.items ?? []) {
      if (!it.menuItemActive) continue;
      m.set(it.categoryName, [...(m.get(it.categoryName) ?? []), it]);
    }
    return [...m.entries()];
  }, [menu.data]);

  if (order.isLoading) return <main className="main center-screen">Đang tải phiếu…</main>;
  if (order.error || !order.data) {
    return (
      <main className="main">
        <ErrorNotice error={order.error} />
        <p style={{ marginTop: '1rem' }}>
          <Link to="/tables" className="btn">Về sơ đồ bàn</Link>
        </p>
      </main>
    );
  }

  const { session, items } = order.data;
  const sessionOpen = session.status === 'OPEN';
  const invoiceLocked = session.invoiceStatus != null && session.invoiceStatus !== 'DRAFT';
  const orderable = canOrder && sessionOpen && !invoiceLocked && menu.data?.status === 'PUBLISHED';
  const live = items.filter((i) => LIVE.includes(i.status));
  const total = live.reduce((s, i) => s + i.unitPrice * i.quantity, 0);
  const draftTotal = draft.reduce((s, d) => s + d.item.price * d.quantity, 0);
  const allEnded = items.every((i) => i.status === 'CANCELLED' || i.status === 'REJECTED');
  const hasServed = items.some((i) => i.status === 'SERVED');
  const unfinished = items.some((i) => ['PENDING', 'ACCEPTED', 'COOKING', 'READY'].includes(i.status));

  function add(item: DailyMenuItem) {
    setDraft((d) => {
      const found = d.find((x) => x.item.dailyMenuItemId === item.dailyMenuItemId);
      if (found) return d.map((x) => (x === found ? { ...x, quantity: x.quantity + 1 } : x));
      return [...d, { item, quantity: 1, note: '' }];
    });
  }

  function setQty(id: number, q: number) {
    setDraft((d) => (q <= 0 ? d.filter((x) => x.item.dailyMenuItemId !== id) : d.map((x) => (x.item.dailyMenuItemId === id ? { ...x, quantity: q } : x))));
  }

  return (
    <main className="main order-page">
      <div className="page-head">
        <div>
          <h1>Bàn {session.tableCode}</h1>
          <p className="muted">
            Ngồi được {elapsed(session.openedAt)} · phiếu #{orderId}
          </p>
        </div>
        <Link to="/tables" className="btn">Về sơ đồ bàn</Link>
      </div>

      <div className="order-layout">
        <section aria-label="Thực đơn hôm nay">
          {!canOrder && <p className="muted">Thu ngân xem phiếu và thu tiền ở cột bên phải.</p>}
          {canOrder && menu.error instanceof ApiError && menu.error.status === 404 && (
            <div className="notice notice-warn">Chưa có thực đơn cho hôm nay. Quản lý cần tạo và công bố thực đơn.</div>
          )}
          {canOrder && menu.data && menu.data.status !== 'PUBLISHED' && (
            <div className="notice notice-warn">
              Thực đơn hôm nay {menu.data.status === 'DRAFT' ? 'chưa được công bố' : 'đã đóng'}, chưa gọi món được.
            </div>
          )}
          {canOrder && invoiceLocked && (
            <div className="notice notice-warn">Bàn đã chốt hóa đơn. Muốn gọi thêm, thu ngân mở lại hóa đơn trước.</div>
          )}
          {canOrder &&
            groups.map(([cat, list]) => (
              <div className="menu-group" key={cat}>
                <h2>{cat}</h2>
                <div className="menu-list">
                  {list.map((it) => {
                    const soldOut = it.remaining != null && it.remaining <= 0;
                    return (
                      <button key={it.dailyMenuItemId} type="button" className="dish" disabled={!orderable || it.paused || soldOut}
                              onClick={() => add(it)}>
                        <span className="name">{it.name}</span>
                        <span className="row">
                          <span className="num">{vnd(it.price)}</span>
                          {it.paused ? (
                            <span className="chip chip-stop">Tạm dừng</span>
                          ) : soldOut ? (
                            <span className="chip chip-stop">Hết suất</span>
                          ) : it.remaining != null ? (
                            <span className={`chip ${it.remaining <= 3 ? 'chip-wait' : ''}`}>Còn {it.remaining}</span>
                          ) : null}
                        </span>
                      </button>
                    );
                  })}
                </div>
              </div>
            ))}
        </section>

        <aside className="ticket" id="ticket" aria-label={`Phiếu bàn ${session.tableCode}`}>
          <div className="ticket-head">
            <span className="code">{session.tableCode}</span>
            {session.invoiceStatus ? <InvoiceStatusChip status={session.invoiceStatus} /> : <span>Đang phục vụ</span>}
          </div>

          {draft.length > 0 && (
            <div className="ticket-section">
              <h3>Món sắp gửi bếp</h3>
              {draft.map((d) => (
                <div className="line" key={d.item.dailyMenuItemId}>
                  <div className="stepper" aria-label={`Số lượng ${d.item.name}`}>
                    <button type="button" onClick={() => setQty(d.item.dailyMenuItemId, d.quantity - 1)} aria-label="Bớt">−</button>
                    <span className="num">{d.quantity}</span>
                    <button type="button" onClick={() => setQty(d.item.dailyMenuItemId, d.quantity + 1)} aria-label="Thêm">+</button>
                  </div>
                  <div className="what">
                    <span>{d.item.name}</span>
                    <input className="input" style={{ minHeight: 34 }} placeholder="Ghi chú cho bếp" maxLength={255} value={d.note}
                           onChange={(e) => setDraft((all) => all.map((x) => (x === d ? { ...x, note: e.target.value } : x)))} />
                  </div>
                  <span className="num">{vnd(d.item.price * d.quantity)}</span>
                </div>
              ))}
              <ErrorNotice error={submit.error} />
              <button type="button" className="btn btn-primary btn-block" disabled={submit.isPending} onClick={() => submit.mutate()}>
                {submit.isPending ? 'Đang gửi…' : `Gửi bếp · ${vnd(draftTotal)}`}
              </button>
            </div>
          )}

          <div className="ticket-section">
            <h3>Đã gọi</h3>
            {items.length === 0 && <p className="muted">Chưa gọi món nào. Chọn món trong thực đơn hôm nay.</p>}
            {items.map((i) => {
              const dead = i.status === 'CANCELLED' || i.status === 'REJECTED';
              return (
                <div className="line" key={i.id} data-dead={dead}>
                  <span className="qty num">{i.quantity}×</span>
                  <div className="what">
                    <span>{i.itemName}</span>
                    {i.note && <span className="muted" style={{ fontSize: 'var(--step--1)' }}>{i.note}</span>}
                    <span><ItemStatusChip status={i.status} /></span>
                  </div>
                  <div className="actions">
                    {canOrder && i.status === 'READY' && (
                      <button type="button" className="btn btn-sm btn-primary" disabled={itemAction.isPending}
                              onClick={() => itemAction.mutate({ id: i.id, action: 'serve' })}>
                        Mang ra
                      </button>
                    )}
                    {canOrder && (i.status === 'PENDING' || i.status === 'ACCEPTED') && (
                      <button type="button" className="btn btn-sm btn-danger" onClick={() => setCancelling(i)}>
                        Hủy
                      </button>
                    )}
                  </div>
                </div>
              );
            })}
            <ErrorNotice error={itemAction.error} />
          </div>

          <div className="ticket-total">
            <span>Tạm tính</span>
            <span className="num">{vnd(session.invoiceTotal ?? total)}</span>
          </div>

          <div className="ticket-foot">
            {canBill && sessionOpen && hasServed && (
              <Link to={`/orders/${orderId}/invoice`} className="btn btn-primary btn-block">
                {session.invoiceStatus === 'PAYMENT_PENDING' ? 'Thu tiền' : unfinished ? 'Xem hóa đơn' : 'Tính tiền'}
              </Link>
            )}
            {canOrder && sessionOpen && allEnded && session.invoiceId == null && (
              <button type="button" className="btn btn-block" onClick={() => setConfirmClose(true)}>
                Trả bàn (không có món)
              </button>
            )}
            {hasRole('MANAGER') && sessionOpen && (
              <button type="button" className="btn btn-danger btn-block" onClick={() => setAbandoning(true)}>
                Khách bỏ về
              </button>
            )}
            <ErrorNotice error={closeEmpty.error ?? abandon.error} />
          </div>
        </aside>
      </div>

      {draft.length > 0 && (
        <button type="button" className="draft-bar" onClick={() => document.getElementById('ticket')?.scrollIntoView({ behavior: 'smooth' })}>
          <span>{draft.reduce((s, d) => s + d.quantity, 0)} món chưa gửi · {vnd(draftTotal)}</span>
          <span>Xem phiếu</span>
        </button>
      )}

      <ReasonDialog
        open={cancelling != null}
        title={`Hủy ${cancelling?.quantity ?? ''} × ${cancelling?.itemName ?? ''}?`}
        description="Món chưa nấu được hủy cả dòng; suất và nguyên liệu được trả lại."
        confirmLabel="Hủy món"
        danger
        onConfirm={(reason) => cancelling && itemAction.mutate({ id: cancelling.id, action: 'cancel', reason })}
        onClose={() => setCancelling(null)}
      />
      <ConfirmDialog
        open={confirmClose}
        title={`Trả bàn ${session.tableCode}?`}
        description="Bàn không có món nào được phục vụ. Phiên đóng lại và bàn chuyển về trống."
        confirmLabel="Trả bàn"
        busy={closeEmpty.isPending}
        onConfirm={() => closeEmpty.mutate()}
        onClose={() => setConfirmClose(false)}
      />
      <ReasonDialog
        open={abandoning}
        title={`Khách bàn ${session.tableCode} bỏ về?`}
        description="Món chưa nấu bị hủy, hóa đơn ghi là không thu được tiền và bàn được trả."
        confirmLabel="Xác nhận khách bỏ về"
        danger
        onConfirm={(reason) => abandon.mutate(reason)}
        onClose={() => setAbandoning(false)}
      />
    </main>
  );
}
