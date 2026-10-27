import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';

import { api, newRequestKey } from '../../api/client';
import type { Receipt, ReceiptHeader } from '../../api/types';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { ErrorNotice } from '../../components/ErrorNotice';
import { DataTable, Field, FormModal, PageHead, num, useAction } from '../../components/ui';
import { amount, dateTime, day, fromLocalInput, vnd } from '../../lib/format';
import { DocStatus, STOCK_KEYS, useIngredients } from './shared';

type Line = { ingredientId: number | ''; inputQuantity: string; inputUnit: string; conversionFactor: string; unitPrice: string; lotCode: string; expiryDate: string };
const blank = (): Line => ({ ingredientId: '', inputQuantity: '', inputUnit: 'kg', conversionFactor: '1000', unitPrice: '', lotCode: '', expiryDate: '' });

/** Phiếu nhập: lập nháp → xác nhận (tạo lô RAW, ghi sổ). Số lượng tồn = số nhập × hệ số quy đổi sang đơn vị gốc. */
export function ReceiptsPage() {
  const [status, setStatus] = useState('');
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState({ supplierName: '', note: '', requestKey: newRequestKey() });
  const [lines, setLines] = useState<Line[]>([blank()]);
  const [confirming, setConfirming] = useState(false);
  const [receivedAt, setReceivedAt] = useState('');
  const [deleting, setDeleting] = useState(false);

  const ings = useIngredients();
  const list = useQuery({ queryKey: ['receipts', status], queryFn: () => api.get<ReceiptHeader[]>(`/api/stock-receipts?size=100${status ? `&status=${status}` : ''}`) });
  const detail = useQuery({ queryKey: ['receipt', selectedId], queryFn: () => api.get<Receipt>(`/api/stock-receipts/${selectedId}`), enabled: selectedId != null });

  const create = useAction(
    () => api.post<Receipt>('/api/stock-receipts', {
      requestKey: form.requestKey,
      supplierName: form.supplierName.trim() || null,
      note: form.note.trim() || null,
      items: lines.map((l) => ({ ingredientId: l.ingredientId, inputQuantity: num(l.inputQuantity), inputUnit: l.inputUnit.trim(),
        conversionFactor: num(l.conversionFactor), unitPricePerInputUnit: num(l.unitPrice) ?? 0, lotCode: l.lotCode.trim() || null, expiryDate: l.expiryDate || null })),
    }),
    { success: 'Đã lập phiếu nhập nháp', invalidate: [['receipts']], onSuccess: (r) => { setCreating(false); setSelectedId(r.id); } },
  );
  const confirm = useAction(() => api.post(`/api/stock-receipts/${selectedId}/confirm`, { receivedAt: fromLocalInput(receivedAt) }), {
    success: 'Đã nhập kho', invalidate: [['receipts'], ['receipt'], ...STOCK_KEYS], onSuccess: () => setConfirming(false),
  });
  const remove = useAction(() => api.del(`/api/stock-receipts/${selectedId}`), {
    success: 'Đã xóa phiếu nháp', invalidate: [['receipts']], onSuccess: () => { setDeleting(false); setSelectedId(null); },
  });

  function startCreate() {
    setForm({ supplierName: '', note: '', requestKey: newRequestKey() });
    setLines([blank()]);
    create.reset();
    setCreating(true);
  }
  const setLine = (i: number, patch: Partial<Line>) => setLines(lines.map((l, j) => (j === i ? { ...l, ...patch } : l)));
  const r = detail.data;
  const total = lines.reduce((s, l) => s + (num(l.inputQuantity) ?? 0) * (num(l.unitPrice) ?? 0), 0);

  return (
    <main className="main">
      <PageHead title="Nhập kho" subtitle="Lập phiếu khi nhận hàng từ nhà cung cấp; kho chỉ tăng khi xác nhận phiếu."
                actions={<button className="btn btn-primary" onClick={startCreate}>Lập phiếu nhập</button>} />
      <div className="split">
        <div>
          <div className="filters">
            <Field label="Trạng thái">
              <select className="input" value={status} onChange={(e) => setStatus(e.target.value)}>
                <option value="">Tất cả</option><option value="DRAFT">Nháp</option><option value="CONFIRMED">Đã xác nhận</option>
              </select>
            </Field>
          </div>
          <ErrorNotice error={list.error} />
          <DataTable rows={list.data} rowKey={(h) => h.id} selectedKey={selectedId} onRowClick={(h) => setSelectedId(h.id)} empty="Chưa có phiếu nhập."
            columns={[
              { header: 'Phiếu', render: (h) => <b>#{h.id}</b> },
              { header: 'Nhà cung cấp', render: (h) => h.supplierName ?? '—' },
              { header: 'Lập lúc', render: (h) => dateTime(h.createdAt) },
              { header: 'Trạng thái', render: (h) => <DocStatus status={h.status} /> },
            ]} />
        </div>
        <aside className="panel stack">
          {!r ? <p className="muted">Chọn một phiếu để xem chi tiết.</p> : (
            <>
              <div className="row-between"><h2>Phiếu nhập #{r.id}</h2><DocStatus status={r.status} /></div>
              <dl className="kv">
                <dt>Nhà cung cấp</dt><dd>{r.supplierName ?? '—'}</dd>
                <dt>Ghi chú</dt><dd>{r.note ?? '—'}</dd>
                <dt>Lập lúc</dt><dd>{dateTime(r.createdAt)}</dd>
                {r.receivedAt && <><dt>Nhận hàng</dt><dd>{dateTime(r.receivedAt)}</dd></>}
                <dt>Tổng tiền</dt><dd className="num">{vnd(r.totalAmount)}</dd>
              </dl>
              {r.lines.map((l) => (
                <div key={l.id} className="stack" style={{ gap: '0.15rem', borderTop: '1px dashed var(--line)', paddingTop: '0.5rem' }}>
                  <div className="row-between"><b>{l.ingredientName}</b><span className="num">{vnd(l.lineAmount)}</span></div>
                  <span className="muted" style={{ fontSize: 'var(--step--1)' }}>
                    {amount(l.inputQuantity, l.inputUnit)} × {amount(l.conversionFactor)} = {amount(l.baseQuantity, l.baseUnit)} · {vnd(l.unitPricePerInputUnit)}/{l.inputUnit}
                    {l.lotCode && ` · lô ${l.lotCode}`}{l.expiryDate && ` · hạn ${day(l.expiryDate)}`}{l.inventoryBatchId && ` · lô kho #${l.inventoryBatchId}`}
                  </span>
                </div>
              ))}
              {r.status === 'DRAFT' && (
                <div className="toolbar">
                  <button className="btn btn-primary" onClick={() => { setReceivedAt(''); confirm.reset(); setConfirming(true); }}>Xác nhận nhập kho</button>
                  <button className="btn btn-danger" onClick={() => setDeleting(true)}>Xóa nháp</button>
                </div>
              )}
              <ErrorNotice error={remove.error} />
            </>
          )}
        </aside>
      </div>

      <FormModal open={creating} wide title="Lập phiếu nhập" submitLabel={`Lưu nháp · ${vnd(total)}`} busy={create.isPending} error={create.error}
                 onSubmit={() => create.mutate(undefined)} onClose={() => setCreating(false)}
                 description="Ví dụ: nhập 10 kg thịt, đơn vị gốc là g → số lượng 10, đơn vị kg, hệ số 1000, giá theo kg.">
        <div className="form-grid">
          <Field label="Nhà cung cấp"><input className="input" maxLength={100} value={form.supplierName} onChange={(e) => setForm({ ...form, supplierName: e.target.value })} /></Field>
          <Field label="Ghi chú"><input className="input" maxLength={255} value={form.note} onChange={(e) => setForm({ ...form, note: e.target.value })} /></Field>
        </div>
        <div className="lines-editor">
          {lines.map((l, i) => {
            const ing = ings.data?.find((g) => g.id === l.ingredientId);
            return (
              <div className="le-row" key={i} style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(120px, 1fr))' }}>
                <Field label="Nguyên liệu">
                  <select className="input" required value={l.ingredientId} onChange={(e) => setLine(i, { ingredientId: e.target.value ? Number(e.target.value) : '' })}>
                    <option value="">Chọn…</option>
                    {ings.data?.map((g) => <option key={g.id} value={g.id}>{g.name} ({g.baseUnit})</option>)}
                  </select>
                </Field>
                <Field label="Số lượng"><input className="input" type="number" min={0} step="any" required value={l.inputQuantity} onChange={(e) => setLine(i, { inputQuantity: e.target.value })} /></Field>
                <Field label="Đơn vị nhập"><input className="input" required maxLength={20} value={l.inputUnit} onChange={(e) => setLine(i, { inputUnit: e.target.value })} /></Field>
                <Field label={`Hệ số → ${ing?.baseUnit ?? 'đơn vị gốc'}`}><input className="input" type="number" min={0} step="any" required value={l.conversionFactor} onChange={(e) => setLine(i, { conversionFactor: e.target.value })} /></Field>
                <Field label="Đơn giá / đơn vị nhập"><input className="input" type="number" min={0} step="any" required value={l.unitPrice} onChange={(e) => setLine(i, { unitPrice: e.target.value })} /></Field>
                <Field label="Mã lô"><input className="input" maxLength={50} value={l.lotCode} onChange={(e) => setLine(i, { lotCode: e.target.value })} /></Field>
                <Field label={ing?.expiryTracked ? 'Hạn dùng (bắt buộc)' : 'Hạn dùng'}><input className="input" type="date" required={ing?.expiryTracked} value={l.expiryDate} onChange={(e) => setLine(i, { expiryDate: e.target.value })} /></Field>
                <button type="button" className="btn btn-quiet" disabled={lines.length === 1} onClick={() => setLines(lines.filter((_, j) => j !== i))}>Bỏ dòng</button>
              </div>
            );
          })}
          <button type="button" className="btn" onClick={() => setLines([...lines, blank()])}>Thêm dòng</button>
        </div>
      </FormModal>

      <FormModal open={confirming} title={`Xác nhận nhập kho phiếu #${selectedId}?`} submitLabel="Xác nhận nhập kho" busy={confirm.isPending} error={confirm.error}
                 onSubmit={() => confirm.mutate(undefined)} onClose={() => setConfirming(false)}
                 description="Mỗi dòng tạo một lô trong kho. Sau khi xác nhận không sửa được phiếu.">
        <Field label="Thời điểm nhận hàng" hint="Để trống = bây giờ.">
          <input className="input" type="datetime-local" value={receivedAt} onChange={(e) => setReceivedAt(e.target.value)} />
        </Field>
      </FormModal>
      <ConfirmDialog open={deleting} title={`Xóa phiếu nháp #${selectedId}?`} confirmLabel="Xóa phiếu" busy={remove.isPending}
                     onConfirm={() => remove.mutate(undefined)} onClose={() => setDeleting(false)} />
    </main>
  );
}
