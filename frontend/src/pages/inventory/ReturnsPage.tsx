import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';

import { api, newRequestKey } from '../../api/client';
import type { Adjustment, AdjustmentHeader, ReturnHeader, StockReturn } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { ErrorNotice } from '../../components/ErrorNotice';
import { DataTable, Field, FormModal, PageHead, Tabs, num, useAction } from '../../components/ui';
import { adjustmentTypeLabel, amount, dateTime } from '../../lib/format';
import { DocStatus, STOCK_KEYS, useHandover, useKitchenLots, useRawLots } from './shared';

/** Nguồn hàng chung cho hoàn trả và hủy hàng: "raw:id" | "wip:issueItemId" | "kitchen:batchId". */
type Source = string;
type Line = { source: Source; quantity: string; note: string };

/** Hoàn trả hàng bếp chưa dùng về kho; hủy hàng hư hỏng/hết hạn. Bếp lập phiếu, quản lý xác nhận. */
export function ReturnsPage() {
  const isManager = useAuth().hasRole('MANAGER');
  const [tab, setTab] = useState<'returns' | 'adjustments'>('returns');
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [creating, setCreating] = useState(false);
  const [requestKey, setRequestKey] = useState(newRequestKey());
  const [reason, setReason] = useState('');
  const [adjType, setAdjType] = useState('DAMAGE');
  const [lines, setLines] = useState<Line[]>([{ source: '', quantity: '', note: '' }]);

  const handover = useHandover();
  const kitchenLots = useKitchenLots();
  const rawLots = useRawLots(isManager && tab === 'adjustments');
  const path = tab === 'returns' ? 'stock-returns' : 'stock-adjustments';
  const list = useQuery({ queryKey: [path], queryFn: () => api.get<(ReturnHeader | AdjustmentHeader)[]>(`/api/${path}?size=100`) });
  const detail = useQuery({ queryKey: [path, selectedId], queryFn: () => api.get<StockReturn | Adjustment>(`/api/${path}/${selectedId}`), enabled: selectedId != null });
  const keys = [[path], ...STOCK_KEYS];

  const wip = (handover.data ?? []).filter((r) => r.stage === 'WIP');
  const options: { value: Source; label: string; max: number }[] = [
    ...(kitchenLots.data ?? [])
      .filter((l) => tab === 'adjustments' || l.directIssueItemId != null)
      .map((l) => ({ value: `kitchen:${l.kitchenBatchId}`, label: `Bếp · ${l.typeName} · lô #${l.kitchenBatchId} · còn ${amount(l.quantityOnHand - l.reservedQuantity)} (không tính phần đang giữ)`, max: l.quantityOnHand - l.reservedQuantity })),
    ...wip.map((r) => ({ value: `wip:${r.stockIssueItemId}`, label: `Chờ sơ chế · ${r.ingredientName} · dòng xuất ${r.stockIssueItemId} · còn ${amount(r.quantity)}`, max: r.quantity })),
    ...(tab === 'adjustments' ? (rawLots.data ?? []).map((l) => ({ value: `raw:${l.inventoryBatchId}`, label: `Kho · ${l.ingredientName} · lô #${l.inventoryBatchId} · còn ${amount(l.quantityOnHand)}`, max: l.quantityOnHand })) : []),
  ];

  const create = useAction(
    () => {
      if (tab === 'returns') {
        return api.post<{ id: number }>('/api/stock-returns', {
          requestKey, reason: reason.trim(),
          items: lines.map((l) => {
            const [kind, id] = l.source.split(':');
            if (kind === 'wip') return { sourceIssueItemId: Number(id), quantity: num(l.quantity) };
            const lot = kitchenLots.data?.find((k) => k.kitchenBatchId === Number(id));
            return { sourceIssueItemId: lot?.directIssueItemId, directKitchenBatchId: Number(id), quantity: num(l.quantity) };
          }),
        });
      }
      return api.post<{ id: number }>('/api/stock-adjustments', {
        requestKey, adjustmentType: adjType, reason: reason.trim(),
        items: lines.map((l) => {
          const [kind, id] = l.source.split(':');
          const key = kind === 'raw' ? 'rawBatchId' : kind === 'wip' ? 'wipIssueItemId' : 'kitchenBatchId';
          return { [key]: Number(id), quantity: num(l.quantity), note: l.note.trim() || null };
        }),
      });
    },
    { success: 'Đã lập phiếu nháp, chờ quản lý xác nhận', invalidate: keys, onSuccess: (r) => { setCreating(false); setSelectedId(r.id); } },
  );
  const confirm = useAction(() => api.post(`/api/${path}/${selectedId}/confirm`), { success: 'Đã xác nhận phiếu', invalidate: [...keys, [path, selectedId]] });

  function start() {
    setRequestKey(newRequestKey());
    setReason('');
    setAdjType('DAMAGE');
    setLines([{ source: '', quantity: '', note: '' }]);
    create.reset();
    setCreating(true);
  }
  const d = detail.data;

  return (
    <main className="main">
      <PageHead title="Hoàn trả, hủy hàng"
                subtitle="Hoàn trả: hàng bếp chưa dùng về lại lô gốc trong kho. Hủy hàng: bỏ hàng hư hỏng, hết hạn khỏi sổ."
                actions={<button className="btn btn-primary" onClick={start}>{tab === 'returns' ? 'Lập phiếu hoàn trả' : 'Lập phiếu hủy hàng'}</button>} />
      <Tabs value={tab} onChange={(t) => { setTab(t); setSelectedId(null); }} options={[['returns', 'Hoàn trả về kho'], ['adjustments', 'Hủy hàng']]} />
      <div className="split">
        <div>
          <ErrorNotice error={list.error} />
          <DataTable rows={list.data} rowKey={(h) => h.id} selectedKey={selectedId} onRowClick={(h) => setSelectedId(h.id)} empty="Chưa có phiếu."
            columns={[
              { header: 'Phiếu', render: (h) => <b>#{h.id}</b> },
              ...(tab === 'adjustments' ? [{ header: 'Loại', render: (h: ReturnHeader | AdjustmentHeader) => adjustmentTypeLabel[(h as AdjustmentHeader).adjustmentType] }] : []),
              { header: 'Lý do', render: (h) => h.reason },
              { header: 'Lập lúc', render: (h) => dateTime(h.createdAt) },
              { header: 'Trạng thái', render: (h) => <DocStatus status={h.status} /> },
            ]} />
        </div>
        <aside className="panel stack">
          {!d ? <p className="muted">Chọn một phiếu để xem chi tiết.</p> : (
            <>
              <div className="row-between"><h2>{tab === 'returns' ? 'Phiếu hoàn trả' : 'Phiếu hủy hàng'} #{d.id}</h2><DocStatus status={d.status} /></div>
              <dl className="kv">
                {'adjustmentType' in d && <><dt>Loại</dt><dd>{adjustmentTypeLabel[d.adjustmentType]}</dd></>}
                <dt>Lý do</dt><dd>{d.reason}</dd>
                <dt>Lập lúc</dt><dd>{dateTime(d.createdAt)}</dd>
                {d.confirmedAt && <><dt>Xác nhận</dt><dd>{dateTime(d.confirmedAt)}</dd></>}
              </dl>
              {'quantityDelta' in (d.lines[0] ?? {})
                ? (d as Adjustment).lines.map((l) => (
                  <div key={l.id} className="row-between" style={{ fontSize: 'var(--step--1)' }}>
                    <span>{l.rawBatchId ? `Kho · lô #${l.rawBatchId}` : l.wipIssueItemId ? `Chờ sơ chế · dòng ${l.wipIssueItemId}` : `Bếp · lô #${l.kitchenBatchId}`}{l.note && <span className="muted"> · {l.note}</span>}</span>
                    <span className="num">{amount(l.quantityDelta)}</span>
                  </div>))
                : (d as StockReturn).lines.map((l) => (
                  <div key={l.id} className="row-between" style={{ fontSize: 'var(--step--1)' }}>
                    <span>Dòng xuất {l.sourceIssueItemId}{l.directKitchenBatchId && ` · lô bếp #${l.directKitchenBatchId}`}</span>
                    <span className="num">{amount(l.quantity)}</span>
                  </div>))}
              {d.status === 'DRAFT' && (isManager
                ? <button className="btn btn-primary" disabled={confirm.isPending} onClick={() => confirm.mutate(undefined)}>Xác nhận phiếu</button>
                : <p className="muted">Chờ quản lý xác nhận.</p>)}
              <ErrorNotice error={confirm.error} />
            </>
          )}
        </aside>
      </div>

      <FormModal open={creating} wide title={tab === 'returns' ? 'Lập phiếu hoàn trả' : 'Lập phiếu hủy hàng'} submitLabel="Lưu nháp"
                 busy={create.isPending} error={create.error} onSubmit={() => create.mutate(undefined)} onClose={() => setCreating(false)}
                 description={tab === 'returns' ? 'Chỉ trả được hàng nhận từ phiếu xuất (dùng thẳng hoặc chờ sơ chế), không trả thành phẩm sơ chế.' : 'Phần đang giữ cho món không hủy được; số liệu kiểm lại khi xác nhận.'}>
        <div className="form-grid">
          {tab === 'adjustments' && (
            <Field label="Loại">
              <select className="input" value={adjType} onChange={(e) => setAdjType(e.target.value)}>
                {Object.entries(adjustmentTypeLabel).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
              </select>
            </Field>
          )}
          <Field label="Lý do" span={tab === 'returns'}><input className="input" required maxLength={255} value={reason} onChange={(e) => setReason(e.target.value)} /></Field>
        </div>
        <div className="lines-editor">
          {lines.map((l, i) => {
            const opt = options.find((o) => o.value === l.source);
            return (
              <div className="le-row" key={i} style={{ gridTemplateColumns: tab === 'adjustments' ? 'minmax(0,3fr) minmax(0,1fr) minmax(0,1.5fr) auto' : 'minmax(0,3fr) minmax(0,1fr) auto' }}>
                <Field label="Hàng">
                  <select className="input" required value={l.source} onChange={(e) => setLines(lines.map((x, j) => (j === i ? { ...x, source: e.target.value } : x)))}>
                    <option value="">Chọn…</option>
                    {options.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
                  </select>
                </Field>
                <Field label="Số lượng"><input className="input" type="number" min={0} max={opt?.max} step="any" required value={l.quantity} onChange={(e) => setLines(lines.map((x, j) => (j === i ? { ...x, quantity: e.target.value } : x)))} /></Field>
                {tab === 'adjustments' && <Field label="Ghi chú"><input className="input" maxLength={255} value={l.note} onChange={(e) => setLines(lines.map((x, j) => (j === i ? { ...x, note: e.target.value } : x)))} /></Field>}
                <button type="button" className="btn btn-quiet" disabled={lines.length === 1} onClick={() => setLines(lines.filter((_, j) => j !== i))}>Bỏ</button>
              </div>
            );
          })}
          <button type="button" className="btn" onClick={() => setLines([...lines, { source: '', quantity: '', note: '' }])}>Thêm dòng</button>
        </div>
      </FormModal>
    </main>
  );
}
