import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';

import { api, newRequestKey } from '../../api/client';
import type { Preparation, PreparationHeader } from '../../api/types';
import { ErrorNotice } from '../../components/ErrorNotice';
import { DataTable, Field, FormModal, PageHead, num, useAction } from '../../components/ui';
import { amount, dateTime, fromLocalInput } from '../../lib/format';
import { DocStatus, STOCK_KEYS, useHandover, useKitchenTypes } from './shared';

type Out = { typeId: number | ''; qty: string; usableUntil: string };

/** Sơ chế: lấy từ hàng chờ sơ chế → ra thành phẩm (lô bếp loại "sau sơ chế"), phần chênh lệch là hao hụt. */
export function PreparationsPage() {
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [creating, setCreating] = useState(false);
  const [requestKey, setRequestKey] = useState(newRequestKey());
  const [wipLine, setWipLine] = useState<number | ''>('');
  const [input, setInput] = useState('');
  const [lossReason, setLossReason] = useState('');
  const [outs, setOuts] = useState<Out[]>([{ typeId: '', qty: '', usableUntil: '' }]);

  const handover = useHandover();
  const types = useKitchenTypes();
  const list = useQuery({ queryKey: ['preparations'], queryFn: () => api.get<PreparationHeader[]>('/api/kitchen-preparations?size=100') });
  const detail = useQuery({ queryKey: ['preparation', selectedId], queryFn: () => api.get<Preparation>(`/api/kitchen-preparations/${selectedId}`), enabled: selectedId != null });
  const keys = [['preparations'], ['preparation'], ...STOCK_KEYS];

  const wip = (handover.data ?? []).filter((r) => r.stage === 'WIP');
  const src = wip.find((r) => r.stockIssueItemId === wipLine);
  const outTypes = (types.data ?? []).filter((t) => t.handlingType === 'PREPARED' && (!src || t.rawIngredientId === src.ingredientId));
  const outTotal = outs.reduce((s, o) => s + (num(o.qty) ?? 0), 0);
  const inQty = num(input) ?? 0;

  const create = useAction(
    () => api.post<Preparation>('/api/kitchen-preparations', {
      requestKey, stockIssueItemId: wipLine, inputQuantity: inQty, lossReason: lossReason.trim() || null,
      outputs: outs.map((o) => ({ kitchenIngredientTypeId: o.typeId, outputQuantity: num(o.qty), usableUntil: fromLocalInput(o.usableUntil) })),
    }),
    { success: 'Đã lập phiếu sơ chế', invalidate: keys, onSuccess: (r) => { setCreating(false); setSelectedId(r.id); } },
  );
  const confirm = useAction(() => api.post(`/api/kitchen-preparations/${selectedId}/confirm`), { success: 'Đã xác nhận sơ chế, thành phẩm vào bếp', invalidate: keys });

  function start() {
    setRequestKey(newRequestKey());
    setWipLine('');
    setInput('');
    setLossReason('');
    setOuts([{ typeId: '', qty: '', usableUntil: '' }]);
    create.reset();
    setCreating(true);
  }
  const d = detail.data;

  return (
    <main className="main">
      <PageHead title="Sơ chế" subtitle="Hàng xuất ở chế độ chờ sơ chế phải qua bước này mới thành nguyên liệu dùng được trong món."
                actions={<button className="btn btn-primary" onClick={start} disabled={wip.length === 0}>Lập phiếu sơ chế</button>} />
      {handover.isSuccess && wip.length === 0 && <div className="notice notice-warn" style={{ marginBottom: '1rem' }}>Không có hàng chờ sơ chế. Kho cần xuất hàng ở chế độ "Chờ sơ chế" và bếp xác nhận đã nhận.</div>}
      <div className="split">
        <div>
          <ErrorNotice error={list.error} />
          <DataTable rows={list.data} rowKey={(h) => h.id} selectedKey={selectedId} onRowClick={(h) => setSelectedId(h.id)} empty="Chưa có phiếu sơ chế."
            columns={[
              { header: 'Phiếu', render: (h) => <b>#{h.id}</b> },
              { header: 'Dòng xuất', render: (h) => h.stockIssueItemId },
              { header: 'Đầu vào', render: (h) => amount(h.inputQuantity), align: 'right' },
              { header: 'Lúc', render: (h) => dateTime(h.preparedAt) },
              { header: 'Trạng thái', render: (h) => <DocStatus status={h.status} /> },
            ]} />
        </div>
        <aside className="panel stack">
          {!d ? <p className="muted">Chọn một phiếu để xem chi tiết.</p> : (
            <>
              <div className="row-between"><h2>Phiếu sơ chế #{d.id}</h2><DocStatus status={d.status} /></div>
              <dl className="kv">
                <dt>Đầu vào</dt><dd className="num">{amount(d.inputQuantity)}</dd>
                <dt>Thành phẩm</dt><dd className="num">{amount(d.outputQuantity)}</dd>
                <dt>Hao hụt</dt><dd className="num">{amount(d.lossQuantity)}{d.inputQuantity > 0 && ` (${Math.round((d.lossQuantity / d.inputQuantity) * 100)}%)`}</dd>
                <dt>Lý do hao hụt</dt><dd>{d.lossReason ?? '—'}</dd>
              </dl>
              {d.outputs.map((o) => (
                <div key={o.id} className="row-between" style={{ fontSize: 'var(--step--1)', borderTop: '1px dashed var(--line)', paddingTop: '0.5rem' }}>
                  <span><b>{o.typeName}</b><br /><span className="muted">dùng trước {dateTime(o.usableUntil)}{o.kitchenBatchId && ` · lô bếp #${o.kitchenBatchId}`}</span></span>
                  <span className="num">{amount(o.outputQuantity)}</span>
                </div>
              ))}
              {d.status === 'DRAFT' && <button className="btn btn-primary" disabled={confirm.isPending} onClick={() => confirm.mutate(undefined)}>Xác nhận sơ chế</button>}
              <ErrorNotice error={confirm.error} />
            </>
          )}
        </aside>
      </div>

      <FormModal open={creating} wide title="Lập phiếu sơ chế" submitLabel="Lưu nháp" busy={create.isPending} error={create.error}
                 onSubmit={() => create.mutate(undefined)} onClose={() => setCreating(false)}
                 description={inQty > 0 ? `Hao hụt dự kiến: ${amount(Math.max(0, inQty - outTotal))} (${Math.round(Math.max(0, inQty - outTotal) / inQty * 100)}%)` : 'Hạn dùng thành phẩm để trống = tự tính theo loại bếp, không vượt hạn lô gốc.'}>
        <div className="form-grid">
          <Field label="Hàng chờ sơ chế" span>
            <select className="input" required value={wipLine} onChange={(e) => setWipLine(e.target.value ? Number(e.target.value) : '')}>
              <option value="">Chọn…</option>
              {wip.map((r) => <option key={r.stockIssueItemId} value={r.stockIssueItemId}>{r.ingredientName} · phiếu xuất #{r.stockIssueId} · còn {amount(r.quantity)}</option>)}
            </select>
          </Field>
          <Field label="Lượng đem sơ chế"><input className="input" type="number" min={0} max={src?.quantity} step="any" required value={input} onChange={(e) => setInput(e.target.value)} /></Field>
          <Field label="Lý do hao hụt"><input className="input" maxLength={255} value={lossReason} onChange={(e) => setLossReason(e.target.value)} placeholder="Gọt vỏ, lọc xương…" /></Field>
        </div>
        <div className="lines-editor">
          {outs.map((o, i) => (
            <div className="le-row" key={i} style={{ gridTemplateColumns: 'minmax(0,2fr) minmax(0,1fr) minmax(0,1.5fr) auto' }}>
              <Field label="Thành phẩm">
                <select className="input" required value={o.typeId} onChange={(e) => setOuts(outs.map((x, j) => (j === i ? { ...x, typeId: e.target.value ? Number(e.target.value) : '' } : x)))}>
                  <option value="">Chọn…</option>
                  {outTypes.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
                </select>
              </Field>
              <Field label="Số lượng"><input className="input" type="number" min={0} step="any" required value={o.qty} onChange={(e) => setOuts(outs.map((x, j) => (j === i ? { ...x, qty: e.target.value } : x)))} /></Field>
              <Field label="Dùng trước"><input className="input" type="datetime-local" value={o.usableUntil} onChange={(e) => setOuts(outs.map((x, j) => (j === i ? { ...x, usableUntil: e.target.value } : x)))} /></Field>
              <button type="button" className="btn btn-quiet" disabled={outs.length === 1} onClick={() => setOuts(outs.filter((_, j) => j !== i))}>Bỏ</button>
            </div>
          ))}
          <button type="button" className="btn" onClick={() => setOuts([...outs, { typeId: '', qty: '', usableUntil: '' }])}>Thêm thành phẩm</button>
          {src && outTypes.length === 0 && <div className="notice notice-warn">{src.ingredientName} chưa có loại bếp "sau sơ chế". Thêm ở màn Nguyên liệu.</div>}
          {outTotal > inQty && inQty > 0 && <div className="notice notice-warn">Tổng thành phẩm vượt lượng đầu vào.</div>}
        </div>
      </FormModal>
    </main>
  );
}
