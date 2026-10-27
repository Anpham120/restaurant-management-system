import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';

import { api, newRequestKey } from '../../api/client';
import type { Issue, IssueHeader } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { ErrorNotice } from '../../components/ErrorNotice';
import { ReasonDialog } from '../../components/ReasonDialog';
import { DataTable, Field, FormModal, PageHead, num, useAction } from '../../components/ui';
import { amount, dateTime, day } from '../../lib/format';
import { DocStatus, STOCK_KEYS, useRawLots } from './shared';

type Line = { batchId: number | ''; quantity: string; mode: 'DIRECT' | 'PREPARE' };

/** Phiếu xuất: kho lập và xuất (trừ lô RAW theo FEFO) → bếp xác nhận đã nhận. */
export function IssuesPage() {
  const isManager = useAuth().hasRole('MANAGER');
  const [status, setStatus] = useState(isManager ? '' : 'ISSUED');
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [creating, setCreating] = useState(false);
  const [note, setNote] = useState('');
  const [requestKey, setRequestKey] = useState(newRequestKey());
  const [lines, setLines] = useState<Line[]>([{ batchId: '', quantity: '', mode: 'DIRECT' }]);
  const [cancelling, setCancelling] = useState(false);

  const lots = useRawLots(isManager);
  const list = useQuery({ queryKey: ['issues', status], queryFn: () => api.get<IssueHeader[]>(`/api/stock-issues?size=100${status ? `&status=${status}` : ''}`), refetchInterval: 15_000 });
  const detail = useQuery({ queryKey: ['issue', selectedId], queryFn: () => api.get<Issue>(`/api/stock-issues/${selectedId}`), enabled: selectedId != null });
  const keys = [['issues'], ['issue'], ...STOCK_KEYS];

  const create = useAction(
    () => api.post<Issue>('/api/stock-issues', {
      requestKey, note: note.trim() || null,
      items: lines.map((l) => ({ inventoryBatchId: l.batchId, quantity: num(l.quantity), fulfillmentMode: l.mode })),
    }),
    { success: 'Đã lập phiếu xuất nháp', invalidate: keys, onSuccess: (r) => { setCreating(false); setSelectedId(r.id); } },
  );
  const act = useAction((a: 'issue' | 'receive') => api.post(`/api/stock-issues/${selectedId}/${a}`), {
    success: (_r, a) => (a === 'issue' ? 'Đã xuất kho, chờ bếp nhận' : 'Bếp đã nhận hàng'), invalidate: keys,
  });
  const cancel = useAction((reason: string) => api.post(`/api/stock-issues/${selectedId}/cancel`, { reason }), {
    success: 'Đã hủy phiếu xuất', invalidate: keys, onSuccess: () => setCancelling(false),
  });

  const setLine = (i: number, patch: Partial<Line>) => setLines(lines.map((l, j) => (j === i ? { ...l, ...patch } : l)));
  const d = detail.data;
  const usableLots = (lots.data ?? []).filter((l) => l.usable);

  return (
    <main className="main">
      <PageHead title="Xuất cho bếp"
                actions={isManager && <button className="btn btn-primary" onClick={() => { setNote(''); setRequestKey(newRequestKey()); setLines([{ batchId: '', quantity: '', mode: 'DIRECT' }]); create.reset(); setCreating(true); }}>Lập phiếu xuất</button>} />
      <div className="split">
        <div>
          <div className="filters">
            <Field label="Trạng thái">
              <select className="input" value={status} onChange={(e) => setStatus(e.target.value)}>
                <option value="">Tất cả</option><option value="DRAFT">Nháp</option><option value="ISSUED">Chờ bếp nhận</option>
                <option value="RECEIVED">Bếp đã nhận</option><option value="CANCELLED">Đã hủy</option>
              </select>
            </Field>
          </div>
          <ErrorNotice error={list.error} />
          <DataTable rows={list.data} rowKey={(h) => h.id} selectedKey={selectedId} onRowClick={(h) => setSelectedId(h.id)} empty="Không có phiếu xuất."
            columns={[
              { header: 'Phiếu', render: (h) => <b>#{h.id}</b> },
              { header: 'Ghi chú', render: (h) => h.note ?? '—' },
              { header: 'Xuất lúc', render: (h) => dateTime(h.issuedAt) },
              { header: 'Trạng thái', render: (h) => <DocStatus status={h.status} /> },
            ]} />
        </div>
        <aside className="panel stack">
          {!d ? <p className="muted pick-hint">Chọn một phiếu để xem chi tiết.</p> : (
            <>
              <div className="row-between"><h2>Phiếu xuất #{d.id}</h2><DocStatus status={d.status} /></div>
              <dl className="kv">
                <dt>Ghi chú</dt><dd>{d.note ?? '—'}</dd>
                <dt>Lập lúc</dt><dd>{dateTime(d.createdAt)}</dd>
                {d.issuedAt && <><dt>Xuất lúc</dt><dd>{dateTime(d.issuedAt)}</dd></>}
                {d.receivedAt && <><dt>Bếp nhận</dt><dd>{dateTime(d.receivedAt)}</dd></>}
                {d.cancelReason && <><dt>Lý do hủy</dt><dd>{d.cancelReason}</dd></>}
              </dl>
              {d.lines.map((l) => (
                <div key={l.id} className="row-between" style={{ borderTop: '1px dashed var(--line)', paddingTop: '0.5rem', fontSize: 'var(--step--1)' }}>
                  <span><b>{l.ingredientName}</b> · lô #{l.inventoryBatchId}{l.lotCode && ` (${l.lotCode})`}<br />
                    <span className="muted">{l.fulfillmentMode === 'DIRECT' ? `Dùng thẳng${l.kitchenBatchId ? ` → lô bếp #${l.kitchenBatchId}` : ''}` : `Chờ sơ chế · dòng ${l.id}${l.wipRemainingQuantity != null ? ` · còn ${amount(l.wipRemainingQuantity)}` : ''}`}</span>
                  </span>
                  <span className="num">{amount(l.quantity)}</span>
                </div>
              ))}
              <div className="toolbar">
                {isManager && d.status === 'DRAFT' && <button className="btn btn-primary" disabled={act.isPending} onClick={() => act.mutate('issue')}>Xuất kho</button>}
                {d.status === 'ISSUED' && <button className="btn btn-primary" disabled={act.isPending} onClick={() => act.mutate('receive')}>Bếp xác nhận đã nhận</button>}
                {isManager && (d.status === 'DRAFT' || d.status === 'ISSUED') && <button className="btn btn-danger" onClick={() => setCancelling(true)}>Hủy phiếu</button>}
              </div>
              <ErrorNotice error={act.error ?? cancel.error} />
            </>
          )}
        </aside>
      </div>

      <FormModal open={creating} wide title="Lập phiếu xuất" submitLabel="Lưu nháp" busy={create.isPending} error={create.error}
                 onSubmit={() => create.mutate(undefined)} onClose={() => setCreating(false)}
                 description="Danh sách lô xếp theo hạn dùng; hệ thống từ chối nếu bỏ qua lô hết hạn sớm hơn.">
        <Field label="Ghi chú"><input className="input" maxLength={255} value={note} onChange={(e) => setNote(e.target.value)} /></Field>
        <div className="lines-editor">
          {lines.map((l, i) => {
            const lot = usableLots.find((x) => x.inventoryBatchId === l.batchId);
            return (
              <div className="le-row" key={i} style={{ gridTemplateColumns: 'minmax(0,3fr) minmax(0,1fr) minmax(0,1.3fr) auto' }}>
                <Field label="Lô trong kho">
                  <select className="input" required value={l.batchId} onChange={(e) => setLine(i, { batchId: e.target.value ? Number(e.target.value) : '' })}>
                    <option value="">Chọn…</option>
                    {usableLots.map((x) => <option key={x.inventoryBatchId} value={x.inventoryBatchId}>{x.ingredientName} · lô #{x.inventoryBatchId}{x.expiryDate ? ` · hạn ${day(x.expiryDate)}` : ''} · còn {amount(x.quantityOnHand)}</option>)}
                  </select>
                </Field>
                <Field label="Số lượng"><input className="input" type="number" min={0} max={lot?.quantityOnHand} step="any" required value={l.quantity} onChange={(e) => setLine(i, { quantity: e.target.value })} /></Field>
                <Field label="Cách dùng">
                  <select className="input" value={l.mode} onChange={(e) => setLine(i, { mode: e.target.value as Line['mode'] })}>
                    <option value="DIRECT">Dùng thẳng</option><option value="PREPARE">Chờ sơ chế</option>
                  </select>
                </Field>
                <button type="button" className="btn btn-quiet" disabled={lines.length === 1} onClick={() => setLines(lines.filter((_, j) => j !== i))}>Bỏ</button>
              </div>
            );
          })}
          <button type="button" className="btn" onClick={() => setLines([...lines, { batchId: '', quantity: '', mode: 'DIRECT' }])}>Thêm dòng</button>
        </div>
      </FormModal>
      <ReasonDialog open={cancelling} title={`Hủy phiếu xuất #${selectedId}?`} description="Nếu đã xuất, hàng được hoàn lại đúng lô trong kho."
                    confirmLabel="Hủy phiếu" danger onConfirm={(r) => cancel.mutate(r)} onClose={() => setCancelling(false)} />
    </main>
  );
}
