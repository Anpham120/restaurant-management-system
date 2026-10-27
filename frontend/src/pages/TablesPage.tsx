import { useState } from 'react';
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';

import { api } from '../api/client';
import type { OpenSessionResponse, SessionResponse, TableResponse } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { ErrorNotice } from '../components/ErrorNotice';
import { elapsed, invoiceStatusLabel, vnd } from '../lib/format';

/** Sơ đồ bàn: bàn trống (ngọc), đang phục vụ (nghệ), chờ thanh toán (ớt). */
export function TablesPage() {
  const { hasRole } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [opening, setOpening] = useState<TableResponse | null>(null);
  const canOpen = hasRole('WAITER', 'MANAGER');

  const tables = useQuery({
    queryKey: ['tables'],
    queryFn: () => api.get<TableResponse[]>('/api/tables'),
    refetchInterval: 10_000,
  });

  const busy = (tables.data ?? []).filter((t) => t.currentSessionId != null);
  const sessions = useQueries({
    queries: busy.map((t) => ({
      queryKey: ['session', t.currentSessionId],
      queryFn: () => api.get<SessionResponse>(`/api/sessions/${t.currentSessionId}`),
      refetchInterval: 10_000,
    })),
  });
  const sessionByTable = new Map<number, SessionResponse>();
  sessions.forEach((q) => q.data && sessionByTable.set(q.data.tableId, q.data));

  const open = useMutation({
    mutationFn: (t: TableResponse) => api.post<OpenSessionResponse>(`/api/tables/${t.id}/sessions`),
    onSuccess: (res) => {
      queryClient.invalidateQueries({ queryKey: ['tables'] });
      setOpening(null);
      navigate(`/orders/${res.orderId}`);
    },
  });

  const now = Date.now();
  const list = tables.data ?? [];
  const freeCount = list.filter((t) => t.status === 'AVAILABLE').length;

  return (
    <main className="main">
      <div className="page-head">
        <div>
          <h1>Sơ đồ bàn</h1>
          <p className="muted">
            {tables.isSuccess ? `${freeCount} bàn trống / ${list.length} bàn` : 'Đang tải…'}
          </p>
        </div>
        <div className="legend" aria-hidden="true">
          <span><i style={{ background: 'var(--jade)' }} />Trống</span>
          <span><i style={{ background: 'var(--turmeric)' }} />Đang phục vụ</span>
          <span><i style={{ background: 'var(--chili)' }} />Chờ thanh toán</span>
        </div>
      </div>

      <ErrorNotice error={tables.error ?? open.error} />

      {tables.isSuccess && list.length === 0 && (
        <div className="center-screen">Chưa có bàn nào. Quản lý thêm bàn trước khi mở ca.</div>
      )}

      <div className="floor">
        {list.map((t) => {
          const s = sessionByTable.get(t.id);
          const state = t.status === 'AVAILABLE' ? 'free' : s?.invoiceStatus === 'PAYMENT_PENDING' ? 'bill' : 'busy';
          const label =
            state === 'free' ? `Trống · ${t.capacity} chỗ` : state === 'bill' ? 'Chờ thanh toán' : 'Đang phục vụ';
          return (
            <button
              key={t.id}
              type="button"
              className="table-tile"
              data-state={state}
              aria-label={`Bàn ${t.code}, ${label}`}
              onClick={() => {
                if (t.currentOrderId != null) navigate(`/orders/${t.currentOrderId}`);
                else if (canOpen) setOpening(t);
              }}
              disabled={t.currentOrderId == null && !canOpen}
            >
              <span className="code">{t.code}</span>
              <span className="meta">
                <b>{label}</b>
                {s && <span>Ngồi được {elapsed(s.openedAt, now)}</span>}
                {s?.invoiceStatus && s.invoiceStatus !== 'PAYMENT_PENDING' && <span>{invoiceStatusLabel[s.invoiceStatus]}</span>}
                {s?.invoiceTotal != null && <span className="num">{vnd(s.invoiceTotal)}</span>}
                {state !== 'free' && <span>{t.capacity} chỗ</span>}
              </span>
            </button>
          );
        })}
      </div>

      <ConfirmDialog
        open={opening != null}
        title={`Mở bàn ${opening?.code ?? ''}?`}
        description="Bàn chuyển sang có khách và mở phiếu gọi món mới."
        confirmLabel="Mở bàn"
        busy={open.isPending}
        onConfirm={() => opening && open.mutate(opening)}
        onClose={() => setOpening(null)}
      />
    </main>
  );
}
