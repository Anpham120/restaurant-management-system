import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';

import { api } from '../../api/client';
import type { KitchenStockRow, RawStockRow, UnusableLotRow } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { ErrorNotice } from '../../components/ErrorNotice';
import { ReasonDialog } from '../../components/ReasonDialog';
import { DataTable, PageHead, StatusTag, Tabs, useAction } from '../../components/ui';
import { amount, dateTime, day } from '../../lib/format';
import { STOCK_KEYS, useHandover, useKitchenLots, useRawLots } from './shared';

type TabKey = 'raw' | 'kitchen' | 'handover' | 'unusable';

/** Tồn kho RAW, tồn bếp, hàng đang bàn giao/chờ sơ chế, lô không dùng được; đánh dấu lô hỏng. */
export function StockPage() {
  const isManager = useAuth().hasRole('MANAGER');
  const [tab, setTab] = useState<TabKey>(isManager ? 'raw' : 'kitchen');
  const [marking, setMarking] = useState<{ kind: 'RAW' | 'KITCHEN'; id: number; label: string } | null>(null);

  const raw = useQuery({ queryKey: ['raw-stock'], queryFn: () => api.get<RawStockRow[]>('/api/inventory/raw-stock'), enabled: isManager && tab === 'raw' });
  const rawLots = useRawLots(isManager && tab === 'raw');
  const kitchen = useQuery({ queryKey: ['kitchen-stock'], queryFn: () => api.get<KitchenStockRow[]>('/api/inventory/kitchen-stock'), enabled: tab === 'kitchen' });
  const kitchenLots = useKitchenLots();
  const handover = useHandover();
  const unusable = useQuery({ queryKey: ['unusable-lots'], queryFn: () => api.get<UnusableLotRow[]>('/api/inventory/unusable-lots'), enabled: tab === 'unusable' });

  const mark = useAction(({ kind, id, reason }: { kind: string; id: number; reason: string }) =>
    api.post<{ affectedOrderItems: number }>(`/api/stock-batches/${kind}/${id}/mark-unusable`, { reason }), {
    success: (r) => (r.affectedOrderItems > 0 ? `Đã đánh dấu lô hỏng; ${r.affectedOrderItems} món đang giữ lô này cần bếp lấy lô khác` : 'Đã đánh dấu lô hỏng'),
    invalidate: [...STOCK_KEYS, ['kitchen-queue']],
    onSuccess: () => setMarking(null),
  });

  const tabs: [TabKey, string][] = isManager
    ? [['raw', 'Kho nguyên liệu'], ['kitchen', 'Tồn bếp'], ['handover', 'Đang bàn giao, chờ sơ chế'], ['unusable', 'Lô không dùng được']]
    : [['kitchen', 'Tồn bếp'], ['handover', 'Đang bàn giao, chờ sơ chế'], ['unusable', 'Lô không dùng được']];

  return (
    <main className="main">
      <PageHead title="Tồn kho" />
      <Tabs value={tab} onChange={setTab} options={tabs} />
      <ErrorNotice error={raw.error ?? kitchen.error ?? handover.error ?? unusable.error ?? mark.error} />

      {tab === 'raw' && (
        <div className="stack">
          <DataTable rows={raw.data} rowKey={(r) => r.ingredientId} empty="Kho chưa có nguyên liệu."
            columns={[
              { header: 'Nguyên liệu', render: (r) => <b>{r.ingredientName}</b> },
              { header: 'Dùng được', render: (r) => amount(r.usableQuantity, r.baseUnit), align: 'right' },
              { header: 'Hỏng', render: (r) => amount(r.unusableQuantity), align: 'right' },
              { header: 'Quá hạn', render: (r) => amount(r.expiredQuantity), align: 'right' },
              { header: 'Tối thiểu', render: (r) => amount(r.minQuantity), align: 'right' },
              { header: '', render: (r) => r.belowMinimum && <StatusTag tone="wait">Dưới mức tối thiểu</StatusTag> },
            ]} />
          <h2>Các lô còn hàng (thứ tự xuất trước)</h2>
          <DataTable rows={rawLots.data} rowKey={(l) => l.inventoryBatchId} empty="Không có lô nào còn hàng."
            columns={[
              { header: 'Lô', render: (l) => <>#{l.inventoryBatchId} {l.lotCode && <span className="muted">· {l.lotCode}</span>}</> },
              { header: 'Nguyên liệu', render: (l) => l.ingredientName },
              { header: 'Hạn dùng', render: (l) => day(l.expiryDate) },
              { header: 'Nhập lúc', render: (l) => dateTime(l.receivedAt) },
              { header: 'Còn', render: (l) => amount(l.quantityOnHand), align: 'right' },
              { header: '', render: (l) => (l.usable
                ? <button className="btn btn-sm btn-danger" onClick={() => setMarking({ kind: 'RAW', id: l.inventoryBatchId, label: `lô #${l.inventoryBatchId} ${l.ingredientName}` })}>Đánh dấu hỏng</button>
                : <StatusTag tone="stop">Không dùng được</StatusTag>) },
            ]} />
        </div>
      )}

      {tab === 'kitchen' && (
        <div className="stack">
          <DataTable rows={kitchen.data} rowKey={(r) => r.kitchenIngredientTypeId} empty="Bếp chưa có hàng."
            columns={[
              { header: 'Nguyên liệu bếp', render: (r) => <b>{r.typeName}</b> },
              { header: 'Dùng được', render: (r) => amount(r.usableOnHand, r.baseUnit), align: 'right' },
              { header: 'Đang giữ cho món', render: (r) => amount(r.reservedQuantity), align: 'right' },
              { header: 'Khả dụng', render: (r) => amount(r.availableQuantity), align: 'right' },
              { header: 'Hỏng / hết hạn', render: (r) => amount(r.unusableOrExpired), align: 'right' },
              { header: '', render: (r) => r.availableQuantity < r.minimumQuantity && <StatusTag tone="wait">Sắp hết</StatusTag> },
            ]} />
          <h2>Các lô tại bếp</h2>
          <DataTable rows={kitchenLots.data} rowKey={(l) => l.kitchenBatchId} empty="Không có lô nào."
            columns={[
              { header: 'Lô bếp', render: (l) => `#${l.kitchenBatchId}` },
              { header: 'Loại', render: (l) => l.typeName },
              { header: 'Dùng trước', render: (l) => dateTime(l.usableUntil) },
              { header: 'Còn', render: (l) => amount(l.quantityOnHand), align: 'right' },
              { header: 'Đang giữ', render: (l) => amount(l.reservedQuantity), align: 'right' },
              { header: '', render: (l) => (!l.usable ? <StatusTag tone="stop">Không dùng được</StatusTag>
                : isManager && <button className="btn btn-sm btn-danger" onClick={() => setMarking({ kind: 'KITCHEN', id: l.kitchenBatchId, label: `lô bếp #${l.kitchenBatchId} ${l.typeName}` })}>Đánh dấu hỏng</button>) },
            ]} />
        </div>
      )}

      {tab === 'handover' && (
        <DataTable rows={handover.data} rowKey={(r) => r.stockIssueItemId} empty="Không có hàng đang bàn giao hay chờ sơ chế."
          columns={[
            { header: 'Giai đoạn', render: (r) => (r.stage === 'IN_TRANSIT' ? <StatusTag tone="wait">Đã xuất, bếp chưa nhận</StatusTag> : <StatusTag>Chờ sơ chế</StatusTag>) },
            { header: 'Nguyên liệu', render: (r) => <b>{r.ingredientName}</b> },
            { header: 'Phiếu xuất', render: (r) => `#${r.stockIssueId} · dòng ${r.stockIssueItemId}` },
            { header: 'Số lượng', render: (r) => amount(r.quantity), align: 'right' },
          ]} />
      )}

      {tab === 'unusable' && (
        <DataTable rows={unusable.data} rowKey={(r) => `${r.location}-${r.batchId}`} empty="Không có lô hỏng hay quá hạn."
          columns={[
            { header: 'Nơi', render: (r) => (r.location === 'RAW' ? 'Kho' : 'Bếp') },
            { header: 'Lô', render: (r) => `#${r.batchId}` },
            { header: 'Hàng', render: (r) => <b>{r.itemName}</b> },
            { header: 'Số lượng', render: (r) => amount(r.quantity), align: 'right' },
            { header: 'Lý do', render: (r) => r.reason ?? 'Quá hạn' },
            { header: 'Hạn', render: (r) => dateTime(r.expires) },
          ]} />
      )}

      <ReasonDialog open={marking != null} title={`Đánh dấu ${marking?.label ?? ''} không dùng được?`}
                    description="Lô không được xuất hay nấu nữa. Món đang giữ lô này sẽ báo để bếp lấy lô khác. Muốn bỏ hẳn hàng khỏi sổ, lập phiếu hủy hàng."
                    confirmLabel="Đánh dấu hỏng" danger onClose={() => setMarking(null)}
                    onConfirm={(reason) => marking && mark.mutate({ kind: marking.kind, id: marking.id, reason })} />
    </main>
  );
}
