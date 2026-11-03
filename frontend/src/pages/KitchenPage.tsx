import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { api } from '../api/client';
import type { AcceptItemResponse, ItemStatus, OrderItem } from '../api/types';
import { ErrorNotice } from '../components/ErrorNotice';
import { ReasonDialog } from '../components/ReasonDialog';
import { useToast } from '../components/Toast';
import { clock, minutesSince } from '../lib/format';

/** 3 cột theo tiến độ (bản thiết kế "Màn hình bếp"). Đã nhận mà chưa nấu nằm chung cột "Đang làm". */
const COLUMNS: { key: 'todo' | 'doing' | 'ready'; title: string; statuses: ItemStatus[] }[] = [
  { key: 'todo', title: 'Chờ nhận', statuses: ['PENDING'] },
  { key: 'doing', title: 'Đang làm', statuses: ['ACCEPTED', 'COOKING'] },
  { key: 'ready', title: 'Xong, chờ bưng', statuses: ['READY'] },
];

/** Màu thời gian chờ: dưới 10 phút xanh, 10–15 cam, quá 15 đỏ. */
function age(submittedAt: string, now: number): 'ok' | 'warn' | 'late' {
  const m = minutesSince(submittedAt, now);
  return m >= 15 ? 'late' : m >= 10 ? 'warn' : 'ok';
}

export function KitchenPage() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [rejecting, setRejecting] = useState<OrderItem | null>(null);
  const [now, setNow] = useState(Date.now());

  useEffect(() => {
    const t = window.setInterval(() => setNow(Date.now()), 30_000);
    return () => window.clearInterval(t);
  }, []);

  const queue = useQuery({
    queryKey: ['kitchen-queue'],
    queryFn: () => api.get<OrderItem[]>('/api/kitchen/queue'),
    refetchInterval: 4_000,
  });

  const act = useMutation({
    mutationFn: ({ item, action, reason }: { item: OrderItem; action: string; reason?: string }) =>
      api.post<AcceptItemResponse | OrderItem>(`/api/order-items/${item.id}/${action}`, reason ? { reason } : undefined),
    onSuccess: (res, v) => {
      if (v.action === 'accept' && 'result' in res && res.result === 'REJECTED') {
        toast(`Không nhận được ${v.item.itemName}: ${res.reason ?? 'thiếu suất hoặc nguyên liệu'}`);
      } else {
        const done: Record<string, string> = {
          accept: 'Đã nhận món',
          reject: 'Đã từ chối món',
          'start-cooking': 'Bắt đầu nấu',
          finish: 'Món đã xong',
          reallocate: 'Đã cấp lại nguyên liệu',
        };
        toast(done[v.action] ?? 'Đã cập nhật');
      }
      setRejecting(null);
      queryClient.invalidateQueries({ queryKey: ['kitchen-queue'] });
    },
  });

  const items = queue.data ?? [];
  // "Cần làm": cộng dồn số suất theo món của các món chưa xong, để bếp nấu gộp
  const todo = new Map<string, number>();
  items.filter((i) => i.status !== 'READY').forEach((i) => todo.set(i.itemName, (todo.get(i.itemName) ?? 0) + i.quantity));

  return (
    <main className="main kitchen-page">
      <div className="page-head">
        <div>
          <h1>Bếp</h1>
          <p className="muted">Tự cập nhật mỗi 4 giây</p>
        </div>
        <div className="legend" aria-label="Màu thời gian chờ">
          <span>Màu thời gian:</span>
          <span className="age-dot" data-age="ok">&lt; 10 phút</span>
          <span className="age-dot" data-age="warn">10–15</span>
          <span className="age-dot" data-age="late">&gt; 15</span>
        </div>
      </div>
      <ErrorNotice error={queue.error ?? act.error} />

      {todo.size > 0 && (
        <section className="todo" aria-label="Tổng số cần làm">
          <b>Cần làm</b>
          {[...todo.entries()].sort((a, b) => b[1] - a[1]).map(([name, qty]) => (
            <span key={name}>
              {name} <b>{qty}</b>
            </span>
          ))}
        </section>
      )}

      <div className="pass">
        {COLUMNS.map((col) => {
          const list = items.filter((i) => col.statuses.includes(i.status));
          return (
            <section className="pass-col" key={col.key} data-col={col.key} aria-label={col.title}>
              <h2>
                <span>{col.title}</span>
                <span className="num">{list.length}</span>
              </h2>
              <div className="stack">
                {list.length === 0 && <div className="empty-col">Không có món</div>}
                {list.map((i) => (
                  <article className="kt" key={i.id} data-age={col.key === 'ready' ? 'done' : age(i.submittedAt, now)}>
                    <div className="top">
                      <span className="table">{i.tableCode}</span>
                      <span className="muted num">{clock(i.submittedAt)}</span>
                      <span className="mins">{minutesSince(i.submittedAt, now)} phút</span>
                    </div>
                    <div className="dishline">
                      <span className="qty num">{i.quantity}×</span>
                      {i.itemName}
                    </div>
                    {i.note && <div className="note">Ghi chú: {i.note}</div>}
                    {i.hasStockException && (
                      <div className="notice notice-warn" style={{ fontSize: 'var(--step--1)' }}>
                        Lô nguyên liệu đang giữ cho món này bị đánh dấu hỏng.
                      </div>
                    )}
                    {col.key === 'ready' ? (
                      <div className="waiting">Đã báo phục vụ bưng ra</div>
                    ) : (
                      <div className="acts">
                        {i.status === 'PENDING' && (
                          <>
                            <button type="button" className="btn btn-primary" disabled={act.isPending}
                                    onClick={() => act.mutate({ item: i, action: 'accept' })}>
                              Nhận món
                            </button>
                            <button type="button" className="btn btn-danger" onClick={() => setRejecting(i)}>
                              Từ chối
                            </button>
                          </>
                        )}
                        {i.status === 'ACCEPTED' &&
                          (i.hasStockException ? (
                            <button type="button" className="btn btn-primary" disabled={act.isPending}
                                    onClick={() => act.mutate({ item: i, action: 'reallocate' })}>
                              Lấy lô khác
                            </button>
                          ) : (
                            <button type="button" className="btn btn-primary" disabled={act.isPending}
                                    onClick={() => act.mutate({ item: i, action: 'start-cooking' })}>
                              Bắt đầu nấu
                            </button>
                          ))}
                        {i.status === 'COOKING' && (
                          <button type="button" className="btn btn-primary" disabled={act.isPending}
                                  onClick={() => act.mutate({ item: i, action: 'finish' })}>
                            Xong
                          </button>
                        )}
                      </div>
                    )}
                  </article>
                ))}
              </div>
            </section>
          );
        })}
      </div>

      <ReasonDialog
        open={rejecting != null}
        title={`Từ chối ${rejecting?.quantity ?? ''} × ${rejecting?.itemName ?? ''} (bàn ${rejecting?.tableCode ?? ''})?`}
        description="Phục vụ sẽ thấy lý do để báo khách."
        confirmLabel="Từ chối món"
        danger
        onConfirm={(reason) => rejecting && act.mutate({ item: rejecting, action: 'reject', reason })}
        onClose={() => setRejecting(null)}
      />
    </main>
  );
}
