import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';

import { api, newRequestKey } from '../../api/client';
import type { Expense } from '../../api/types';
import { ErrorNotice } from '../../components/ErrorNotice';
import { ReasonDialog } from '../../components/ReasonDialog';
import { DataTable, Field, FormModal, PageHead, StatusTag, num, useAction } from '../../components/ui';
import { addDays, businessToday, dateTime, day, expenseCategoryLabel, fromLocalInput, vnd } from '../../lib/format';

const FUND: Record<string, string> = { CASH_DRAWER: 'Tiền quầy', OWNER: 'Chủ chi', BANK: 'Tài khoản ngân hàng' };
const METHOD: Record<string, string> = { CASH: 'Tiền mặt', BANK_TRANSFER: 'Chuyển khoản' };

/** Khoản chi hoạt động. Ghi sai thì hủy và ghi lại, không sửa. Chi từ tiền quầy được trừ khi đối soát tiền mặt. */
export function ExpensesPage() {
  const today = businessToday();
  const [from, setFrom] = useState(addDays(today, -6));
  const [to, setTo] = useState(today);
  const [category, setCategory] = useState('');
  const [status, setStatus] = useState('PAID');
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState({ requestKey: newRequestKey(), category: 'SUPPLIES', description: '', amount: '', paymentMethod: 'CASH', fundSource: 'CASH_DRAWER', stockReceiptId: '', paidAt: '' });
  const [voiding, setVoiding] = useState<Expense | null>(null);

  const list = useQuery({
    queryKey: ['expenses', from, to, category, status],
    queryFn: () => api.get<Expense[]>(`/api/expenses?from=${from}&to=${to}${category ? `&category=${category}` : ''}${status ? `&status=${status}` : ''}`),
  });
  const keys = [['expenses'], ['cash-recon'], ['expense-report']];
  const create = useAction(
    () => api.post('/api/expenses', {
      requestKey: form.requestKey, category: form.category, description: form.description.trim(), amount: num(form.amount),
      paymentMethod: form.paymentMethod, fundSource: form.fundSource, stockReceiptId: num(form.stockReceiptId), paidAt: fromLocalInput(form.paidAt),
    }),
    { success: 'Đã ghi khoản chi', invalidate: keys, onSuccess: () => setCreating(false) },
  );
  const voidAction = useAction((reason: string) => api.post(`/api/expenses/${voiding!.id}/void`, { reason }), {
    success: 'Đã hủy khoản chi', invalidate: keys, onSuccess: () => setVoiding(null),
  });

  const total = (list.data ?? []).filter((e) => e.status === 'PAID').reduce((s, e) => s + e.amount, 0);
  const drawerOnly = form.fundSource === 'CASH_DRAWER';

  return (
    <main className="main">
      <PageHead title="Khoản chi" subtitle={list.data ? `Tổng chi đã trả trong kỳ: ${vnd(total)}` : undefined}
                actions={<button className="btn btn-primary" onClick={() => { setForm({ requestKey: newRequestKey(), category: 'SUPPLIES', description: '', amount: '', paymentMethod: 'CASH', fundSource: 'CASH_DRAWER', stockReceiptId: '', paidAt: '' }); create.reset(); setCreating(true); }}>Ghi khoản chi</button>} />
      <div className="filters">
        <Field label="Từ ngày"><input className="input" type="date" value={from} max={to} onChange={(e) => e.target.value && setFrom(e.target.value)} /></Field>
        <Field label="Đến ngày"><input className="input" type="date" value={to} min={from} onChange={(e) => e.target.value && setTo(e.target.value)} /></Field>
        <Field label="Nhóm">
          <select className="input" value={category} onChange={(e) => setCategory(e.target.value)}>
            <option value="">Tất cả</option>{Object.entries(expenseCategoryLabel).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </Field>
        <Field label="Trạng thái">
          <select className="input" value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="">Tất cả</option><option value="PAID">Đã chi</option><option value="VOIDED">Đã hủy</option>
          </select>
        </Field>
      </div>
      <ErrorNotice error={list.error} />
      <DataTable rows={list.data} rowKey={(e) => e.id} empty="Không có khoản chi trong khoảng này."
        columns={[
          { header: 'Ngày', render: (e) => day(e.businessDate) },
          { header: 'Nội dung', render: (e) => <><b>{e.description}</b><div className="muted">{expenseCategoryLabel[e.category]}{e.stockReceiptId && ` · phiếu nhập #${e.stockReceiptId}`}</div></> },
          { header: 'Nguồn tiền', render: (e) => `${FUND[e.fundSource]} · ${METHOD[e.paymentMethod]}` },
          { header: 'Chi lúc', render: (e) => dateTime(e.paidAt) },
          { header: 'Số tiền', render: (e) => <span style={{ textDecoration: e.status === 'VOIDED' ? 'line-through' : undefined }}>{vnd(e.amount)}</span>, align: 'right' },
          { header: '', render: (e) => (e.status === 'VOIDED'
            ? <span title={e.voidReason ?? ''}><StatusTag tone="stop">Đã hủy</StatusTag></span>
            : <button className="btn btn-sm btn-danger" onClick={() => { voidAction.reset(); setVoiding(e); }}>Hủy</button>) },
        ]} />

      <FormModal open={creating} title="Ghi khoản chi" submitLabel="Ghi khoản chi" busy={create.isPending} error={create.error}
                 onSubmit={() => create.mutate(undefined)} onClose={() => setCreating(false)} description="Ngày kinh doanh tính theo thời điểm chi (mốc 4 giờ sáng).">
        <div className="form-grid">
          <Field label="Nội dung" span><input className="input" required maxLength={255} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} autoFocus /></Field>
          <Field label="Nhóm">
            <select className="input" value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })}>
              {Object.entries(expenseCategoryLabel).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
          </Field>
          <Field label="Số tiền (đồng)"><input className="input" type="number" min={1} step="any" required value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} /></Field>
          <Field label="Nguồn tiền">
            <select className="input" value={form.fundSource} onChange={(e) => setForm({ ...form, fundSource: e.target.value, paymentMethod: e.target.value === 'CASH_DRAWER' ? 'CASH' : form.paymentMethod })}>
              {Object.entries(FUND).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
          </Field>
          <Field label="Hình thức" hint={drawerOnly ? 'Tiền quầy chỉ chi bằng tiền mặt.' : undefined}>
            <select className="input" value={form.paymentMethod} disabled={drawerOnly} onChange={(e) => setForm({ ...form, paymentMethod: e.target.value })}>
              {Object.entries(METHOD).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
          </Field>
          <Field label="Thời điểm chi" hint="Để trống = bây giờ."><input className="input" type="datetime-local" value={form.paidAt} onChange={(e) => setForm({ ...form, paidAt: e.target.value })} /></Field>
          <Field label="Phiếu nhập kho (nếu có)" hint="Mỗi phiếu nhập chỉ gắn một khoản chi."><input className="input" type="number" min={1} value={form.stockReceiptId} onChange={(e) => setForm({ ...form, stockReceiptId: e.target.value })} /></Field>
        </div>
      </FormModal>
      <ReasonDialog open={voiding != null} title={`Hủy khoản chi ${voiding ? vnd(voiding.amount) : ''}?`} description="Khoản chi bị hủy không tính vào báo cáo và đối soát. Không hoàn tác."
                    confirmLabel="Hủy khoản chi" danger onConfirm={(r) => voidAction.mutate(r)} onClose={() => setVoiding(null)} />
      <ErrorNotice error={voidAction.error} />
    </main>
  );
}
