import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';

import { api } from '../../api/client';
import type { KitchenStockRow, LotExpiryStatus, RawStockRow, UnusableLotRow } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { ErrorNotice } from '../../components/ErrorNotice';
import { ReasonDialog } from '../../components/ReasonDialog';
import { DataTable, PageHead, StatusTag, Tabs, useAction } from '../../components/ui';
import { amount, dateTime, day } from '../../lib/format';
import { STOCK_KEYS, useHandover, useKitchenLots, useRawLots } from './shared';

type TabKey = 'raw' | 'kitchen' | 'handover' | 'unusable';
type ExpiryAlert = {
  kind: 'raw' | 'kitchen';
  id: number;
  name: string;
  expiry: string;
  receivedAt: string;
  onHand: number;
  reserved: number;
  unit: string;
  status: LotExpiryStatus;
};

/** Tồn kho RAW, tồn bếp, hàng đang bàn giao/chờ sơ chế, lô không dùng được; đánh dấu lô hỏng. */
export function StockPage() {
  const isManager = useAuth().hasRole('MANAGER');
  const [tab, setTab] = useState<TabKey>(isManager ? 'raw' : 'kitchen');
  const [marking, setMarking] = useState<{ kind: 'RAW' | 'KITCHEN'; id: number; label: string } | null>(null);

  const raw = useQuery({ queryKey: ['raw-stock'], queryFn: () => api.get<RawStockRow[]>('/api/inventory/raw-stock'), enabled: isManager && tab === 'raw' });
  const rawLots = useRawLots(isManager);
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

  const alerts: ExpiryAlert[] = [
    ...(isManager ? rawLots.data ?? [] : []).map((lot): ExpiryAlert => ({
      kind: 'raw', id: lot.inventoryBatchId, name: lot.ingredientName, expiry: day(lot.expiryDate),
      receivedAt: lot.receivedAt, onHand: lot.quantityOnHand, reserved: 0, unit: lot.baseUnit, status: lot.expiryStatus,
    })),
    ...(kitchenLots.data ?? []).map((lot): ExpiryAlert => ({
      kind: 'kitchen', id: lot.kitchenBatchId, name: lot.typeName, expiry: dateTime(lot.usableUntil),
      receivedAt: lot.createdAt, onHand: lot.quantityOnHand, reserved: lot.reservedQuantity, unit: lot.baseUnit, status: lot.expiryStatus,
    })),
  ].filter((lot) => lot.status === 'EXPIRED' || lot.status === 'EXPIRING_SOON')
    .sort((a, b) => Date.parse(a.receivedAt) - Date.parse(b.receivedAt) || a.id - b.id);

  return (
    <main className="main">
      <PageHead title="Tồn kho" />
      <ErrorNotice error={raw.error ?? rawLots.error ?? kitchen.error ?? kitchenLots.error ?? handover.error ?? unusable.error ?? mark.error} />
      {alerts.length > 0 && (
        <section className="panel stack" aria-labelledby="lot-expiry-heading">
          <h2 id="lot-expiry-heading">Cảnh báo hạn dùng</h2>
          <p className="muted">Lô sắp hết hạn trong 7 ngày và lô đã hết hạn còn trong sổ. Lô nhập trước hiển thị trước. Quản lý xác nhận phiếu hủy để trừ số lượng.</p>
          <DataTable rows={alerts} rowKey={(lot) => `${lot.kind}-${lot.id}`} empty="Không có lô sắp hết hạn hoặc đã hết hạn."
            columns={[
              { header: 'Lô', render: (lot) => <>{lot.kind === 'raw' ? 'Kho' : 'Bếp'} · <b>{lot.name}</b> · #{lot.id}</> },
              { header: 'Vào kho / bếp', render: (lot) => dateTime(lot.receivedAt) },
              { header: 'Hạn dùng', render: (lot) => lot.expiry },
              { header: 'Còn trong sổ', render: (lot) => <>{amount(lot.onHand, lot.unit)}{lot.reserved > 0 && <div className="muted">Đang giữ {amount(lot.reserved, lot.unit)}</div>}</>, align: 'right' },
              { header: 'Trạng thái', render: (lot) => lot.status === 'EXPIRED' ? <StatusTag tone="stop">Đã hết hạn</StatusTag> : <StatusTag tone="wait">Sắp hết hạn</StatusTag> },
              { header: '', render: (lot) => lot.status === 'EXPIRED' && (lot.onHand > lot.reserved
                ? <Link className="btn btn-sm" to={`/inventory/returns?expired=${lot.kind}:${lot.id}`}>Lập phiếu hủy</Link>
                : <small className="muted">Bếp cần cấp lại nguyên liệu</small>) },
            ]} />
        </section>
      )}
      <Tabs value={tab} onChange={setTab} options={tabs} />

      {tab === 'raw' && (
        <div className="stack">
          <DataTable rows={raw.data} rowKey={(r) => r.ingredientId} empty="Kho chưa có nguyên liệu."
            columns={[
              { header: 'Nguyên liệu', render: (r) => <b>{r.ingredientName}</b> },
              { header: 'Dùng được', render: (r) => amount(r.usableQuantity, r.baseUnit), align: 'right' },
              { header: 'Hỏng', render: (r) => amount(r.unusableQuantity, r.baseUnit), align: 'right' },
              { header: 'Quá hạn', render: (r) => amount(r.expiredQuantity, r.baseUnit), align: 'right' },
              { header: 'Tối thiểu', render: (r) => amount(r.minQuantity, r.baseUnit), align: 'right' },
              { header: '', render: (r) => r.belowMinimum && <StatusTag tone="wait">Dưới mức tối thiểu</StatusTag> },
            ]} />
          <h2>Các lô còn hàng (thứ tự xuất trước)</h2>
          <DataTable rows={rawLots.data} rowKey={(l) => l.inventoryBatchId} empty="Không có lô nào còn hàng."
            columns={[
              { header: 'Lô', render: (l) => <>#{l.inventoryBatchId} {l.lotCode && <span className="muted">· {l.lotCode}</span>}</> },
              { header: 'Nguyên liệu', render: (l) => l.ingredientName },
              { header: 'Hạn dùng', render: (l) => day(l.expiryDate) },
              { header: 'Nhập lúc', render: (l) => dateTime(l.receivedAt) },
              { header: 'Còn', render: (l) => amount(l.quantityOnHand, l.baseUnit), align: 'right' },
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
              { header: 'Đang giữ cho món', render: (r) => amount(r.reservedQuantity, r.baseUnit), align: 'right' },
              { header: 'Khả dụng', render: (r) => amount(r.availableQuantity, r.baseUnit), align: 'right' },
              { header: 'Hỏng / hết hạn', render: (r) => amount(r.unusableOrExpired, r.baseUnit), align: 'right' },
              { header: '', render: (r) => r.availableQuantity < r.minimumQuantity && <StatusTag tone="wait">Sắp hết</StatusTag> },
            ]} />
          <h2>Các lô tại bếp</h2>
          <DataTable rows={kitchenLots.data} rowKey={(l) => l.kitchenBatchId} empty="Không có lô nào."
            columns={[
              { header: 'Lô bếp', render: (l) => `#${l.kitchenBatchId}` },
              { header: 'Loại', render: (l) => l.typeName },
              { header: 'Dùng trước', render: (l) => dateTime(l.usableUntil) },
              { header: 'Còn', render: (l) => amount(l.quantityOnHand, l.baseUnit), align: 'right' },
              { header: 'Đang giữ', render: (l) => amount(l.reservedQuantity, l.baseUnit), align: 'right' },
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
            { header: 'Số lượng', render: (r) => amount(r.quantity, r.baseUnit), align: 'right' },
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
