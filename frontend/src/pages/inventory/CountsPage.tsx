import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';

import { api, newRequestKey } from '../../api/client';
import type { Count, CountHeader } from '../../api/types';
import { ErrorNotice } from '../../components/ErrorNotice';
import { DataTable, Field, FormModal, PageHead, num, useAction } from '../../components/ui';
import { amount, dateTime } from '../../lib/format';
import { DocStatus, STOCK_KEYS, useHandover, useKitchenLots, useRawLots } from './shared';

type Line = { source: string; actual: string; reason: string };

/** Kiểm kê: số sổ được chụp khi lập phiếu; xác nhận ghi chênh lệch. Sổ đổi sau khi lập → phải lập lại. */
export function CountsPage() {
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [creating, setCreating] = useState(false);
  const [requestKey, setRequestKey] = useState(newRequestKey());
  const [note, setNote] = useState('');
  const [lines, setLines] = useState<Line[]>([{ source: '', actual: '', reason: '' }]);

  const raw = useRawLots();
  const kitchen = useKitchenLots();
  const handover = useHandover();
  const list = useQuery({ queryKey: ['counts'], queryFn: () => api.get<CountHeader[]>('/api/stock-counts?size=100') });
  const detail = useQuery({ queryKey: ['count', selectedId], queryFn: () => api.get<Count>(`/api/stock-counts/${selectedId}`), enabled: selectedId != null });
  const keys = [['counts'], ['count'], ...STOCK_KEYS];

  const options = [
    ...(raw.data ?? []).map((l) => ({ value: `raw:${l.inventoryBatchId}`, label: `Kho · ${l.ingredientName} · lô #${l.inventoryBatchId}`, book: l.quantityOnHand })),
    ...(handover.data ?? []).filter((r) => r.stage === 'WIP').map((r) => ({ value: `wip:${r.stockIssueItemId}`, label: `Chờ sơ chế · ${r.ingredientName} · dòng ${r.stockIssueItemId}`, book: r.quantity })),
    ...(kitchen.data ?? []).map((l) => ({ value: `kitchen:${l.kitchenBatchId}`, label: `Bếp · ${l.typeName} · lô #${l.kitchenBatchId}`, book: l.quantityOnHand })),
  ];

  const create = useAction(
    () => api.post<Count>('/api/stock-counts', {
      requestKey, note: note.trim() || null,
      items: lines.map((l) => {
        const [kind, id] = l.source.split(':');
        const key = kind === 'raw' ? 'rawBatchId' : kind === 'wip' ? 'wipIssueItemId' : 'kitchenBatchId';
        return { [key]: Number(id), actualQuantity: num(l.actual), reason: l.reason.trim() || null };
      }),
    }),
    { success: 'Đã lập phiếu kiểm kê', invalidate: keys, onSuccess: (r) => { setCreating(false); setSelectedId(r.id); } },
  );
  const confirm = useAction(() => api.post(`/api/stock-counts/${selectedId}/confirm`), { success: 'Đã ghi chênh lệch kiểm kê', invalidate: keys });

  const d = detail.data;
  return (
    <main className="main">
      <PageHead title="Kiểm kê"
                actions={<button className="btn btn-primary" onClick={() => { setRequestKey(newRequestKey()); setNote(''); setLines([{ source: '', actual: '', reason: '' }]); create.reset(); setCreating(true); }}>Lập phiếu kiểm kê</button>} />
      <div className="split">
        <div>
          <ErrorNotice error={list.error} />
          <DataTable rows={list.data} rowKey={(h) => h.id} selectedKey={selectedId} onRowClick={(h) => setSelectedId(h.id)} empty="Chưa có phiếu kiểm kê."
            columns={[
              { header: 'Phiếu', render: (h) => <b>#{h.id}</b> },
              { header: 'Ghi chú', render: (h) => h.note ?? '—' },
              { header: 'Lập lúc', render: (h) => dateTime(h.createdAt) },
              { header: 'Trạng thái', render: (h) => <DocStatus status={h.status} /> },
            ]} />
        </div>
        <aside className="panel stack">
          {!d ? <p className="muted pick-hint">Chọn một phiếu để xem chi tiết.</p> : (
            <>
              <div className="row-between"><h2>Phiếu kiểm kê #{d.id}</h2><DocStatus status={d.status} /></div>
              <DataTable rows={d.lines} rowKey={(l) => l.id} empty="—"
                columns={[
                  { header: 'Lô', render: (l) => (l.rawBatchId ? `Kho #${l.rawBatchId}` : l.wipIssueItemId ? `Sơ chế · dòng ${l.wipIssueItemId}` : `Bếp #${l.kitchenBatchId}`) },
                  { header: 'Sổ', render: (l) => amount(l.bookQuantity), align: 'right' },
                  { header: 'Đếm', render: (l) => amount(l.actualQuantity), align: 'right' },
                  { header: 'Chênh', render: (l) => <span style={{ color: l.difference < 0 ? 'var(--chili)' : undefined }}>{amount(l.difference)}</span>, align: 'right' },
                ]} />
              {d.status === 'DRAFT' && <button className="btn btn-primary" disabled={confirm.isPending} onClick={() => confirm.mutate(undefined)}>Xác nhận kiểm kê</button>}
              <ErrorNotice error={confirm.error} />
            </>
          )}
        </aside>
      </div>

      <FormModal open={creating} wide title="Lập phiếu kiểm kê" submitLabel="Lưu nháp" busy={create.isPending} error={create.error}
                 onSubmit={() => create.mutate(undefined)} onClose={() => setCreating(false)}>
        <Field label="Ghi chú"><input className="input" maxLength={255} value={note} onChange={(e) => setNote(e.target.value)} /></Field>
        <div className="lines-editor">
          {lines.map((l, i) => {
            const opt = options.find((o) => o.value === l.source);
            return (
              <div className="le-row" key={i} style={{ gridTemplateColumns: 'minmax(0,3fr) minmax(0,1fr) minmax(0,1.5fr) auto' }}>
                <Field label={opt ? `Lô (sổ: ${amount(opt.book)})` : 'Lô'}>
                  <select className="input" required value={l.source} onChange={(e) => setLines(lines.map((x, j) => (j === i ? { ...x, source: e.target.value } : x)))}>
                    <option value="">Chọn…</option>
                    {options.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
                  </select>
                </Field>
                <Field label="Đếm thực tế"><input className="input" type="number" min={0} step="any" required value={l.actual} onChange={(e) => setLines(lines.map((x, j) => (j === i ? { ...x, actual: e.target.value } : x)))} /></Field>
                <Field label="Lý do chênh lệch"><input className="input" maxLength={255} value={l.reason} onChange={(e) => setLines(lines.map((x, j) => (j === i ? { ...x, reason: e.target.value } : x)))} /></Field>
                <button type="button" className="btn btn-quiet" disabled={lines.length === 1} onClick={() => setLines(lines.filter((_, j) => j !== i))}>Bỏ</button>
              </div>
            );
          })}
          <button type="button" className="btn" onClick={() => setLines([...lines, { source: '', actual: '', reason: '' }])}>Thêm lô</button>
        </div>
      </FormModal>
    </main>
  );
}
