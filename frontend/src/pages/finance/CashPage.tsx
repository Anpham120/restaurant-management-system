import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';

import { api } from '../../api/client';
import type { CashReconciliation } from '../../api/types';
import { ErrorNotice } from '../../components/ErrorNotice';
import { DataTable, Field, FormModal, PageHead, StatusTag, num, useAction } from '../../components/ui';
import { addDays, businessToday, dateTime, day, vnd } from '../../lib/format';

/** Đối soát tiền mặt cuối ngày: quỹ đầu ngày + thu tiền mặt − chi từ quầy = tiền phải có; so với tiền đếm thực tế. */
export function CashPage() {
  const [date, setDate] = useState(businessToday());
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ openingCash: '', actualCash: '', reason: '', revisionReason: '' });

  const recon = useQuery({ queryKey: ['cash-recon', date], queryFn: () => api.get<CashReconciliation>(`/api/cash-reconciliations/${date}`), refetchInterval: 30_000 });
  const save = useAction(
    () => api.post('/api/cash-reconciliations', {
      businessDate: date, openingCash: num(form.openingCash), actualCash: num(form.actualCash),
      reason: form.reason.trim() || null, revisionReason: form.revisionReason.trim() || null,
    }),
    { success: 'Đã ghi đối soát', invalidate: [['cash-recon', date]], onSuccess: () => setOpen(false) },
  );

  const r = recon.data;
  const opening = num(form.openingCash) ?? 0;
  const expected = r ? opening + r.cashSales - r.cashExpenses : 0;
  const diff = (num(form.actualCash) ?? 0) - expected;
  const isRevision = (r?.versions.length ?? 0) > 0;

  return (
    <main className="main">
      <PageHead title="Đối soát tiền mặt" subtitle={`Ngày kinh doanh ${day(date)}`}
                actions={<>
                  <button className="btn btn-sm" onClick={() => setDate(addDays(date, -1))}>Ngày trước</button>
                  <input className="input" type="date" value={date} max={businessToday()} onChange={(e) => e.target.value && setDate(e.target.value)} style={{ width: 170 }} />
                  <button className="btn btn-sm" disabled={date >= businessToday()} onClick={() => setDate(addDays(date, 1))}>Ngày sau</button>
                  <button className="btn btn-primary" disabled={!r} onClick={() => {
                    setForm({ openingCash: r?.openingCash != null ? String(r.openingCash) : '', actualCash: '', reason: '', revisionReason: '' });
                    save.reset();
                    setOpen(true);
                  }}>{isRevision ? 'Đối soát lại' : 'Đối soát'}</button>
                </>} />
      <ErrorNotice error={recon.error} />
      {r && (
        <>
          {r.stale && <div className="notice notice-warn" style={{ marginBottom: '1rem' }}>Có thu hoặc chi tiền mặt sau lần đối soát gần nhất. Số dưới đây đã tính lại; hãy đối soát lại.</div>}
          <div className="stats">
            <div className="stat"><div className="label">Quỹ đầu ngày</div><div className="value num">{vnd(r.openingCash)}</div></div>
            <div className="stat"><div className="label">Thu tiền mặt</div><div className="value num">{vnd(r.cashSales)}</div></div>
            <div className="stat"><div className="label">Chi từ tiền quầy</div><div className="value num">{vnd(r.cashExpenses)}</div></div>
            <div className="stat"><div className="label">Tiền phải có</div><div className="value num">{vnd(r.expectedCash)}</div></div>
            <div className="stat"><div className="label">Tiền đếm được</div><div className="value num">{vnd(r.actualCash)}</div></div>
            <div className="stat">
              <div className="label">Chênh lệch</div>
              <div className="value num" style={{ color: r.difference && r.difference < 0 ? 'var(--chili)' : r.difference ? 'var(--turmeric)' : undefined }}>{vnd(r.difference)}</div>
            </div>
          </div>
          {r.reason && <p style={{ marginBottom: '1rem' }}>Lý do chênh lệch: {r.reason}</p>}
          <h2 style={{ marginBottom: '0.75rem' }}>Các lần đối soát</h2>
          <DataTable rows={r.versions} rowKey={(v) => v.id} empty="Ngày này chưa đối soát."
            columns={[
              { header: 'Lần', render: (v) => <>{v.version} {v.status === 'CURRENT' ? <StatusTag tone="go">Hiện hành</StatusTag> : <StatusTag>Cũ</StatusTag>}</> },
              { header: 'Lúc', render: (v) => dateTime(v.reconciledAt) },
              { header: 'Phải có', render: (v) => vnd(v.expectedCash), align: 'right' },
              { header: 'Đếm được', render: (v) => vnd(v.actualCash), align: 'right' },
              { header: 'Chênh', render: (v) => vnd(v.difference), align: 'right' },
              { header: 'Ghi chú', render: (v) => [v.reason, v.revisionReason && `Đối soát lại: ${v.revisionReason}`].filter(Boolean).join(' · ') },
            ]} />
        </>
      )}

      <FormModal open={open} title={`${isRevision ? 'Đối soát lại' : 'Đối soát'} ngày ${day(date)}`} submitLabel="Ghi đối soát" busy={save.isPending} error={save.error}
                 onSubmit={() => save.mutate(undefined)} onClose={() => setOpen(false)}
                 description={r ? `Thu tiền mặt ${vnd(r.cashSales)}, chi từ quầy ${vnd(r.cashExpenses)}.` : undefined}>
        <div className="form-grid">
          <Field label="Quỹ đầu ngày"><input className="input" type="number" min={0} step="any" required value={form.openingCash} onChange={(e) => setForm({ ...form, openingCash: e.target.value })} autoFocus /></Field>
          <Field label="Tiền đếm thực tế"><input className="input" type="number" min={0} step="any" required value={form.actualCash} onChange={(e) => setForm({ ...form, actualCash: e.target.value })} /></Field>
        </div>
        {form.openingCash !== '' && form.actualCash !== '' && (
          <div className={diff === 0 ? 'notice notice-ok' : 'notice notice-warn'}>
            Tiền phải có {vnd(expected)} · {diff === 0 ? 'khớp' : `${diff > 0 ? 'thừa' : 'thiếu'} ${vnd(Math.abs(diff))}`}
          </div>
        )}
        <div className="form-grid">
          <Field label={diff !== 0 ? 'Lý do chênh lệch (bắt buộc)' : 'Lý do chênh lệch'} span>
            <input className="input" required={diff !== 0} maxLength={255} value={form.reason} onChange={(e) => setForm({ ...form, reason: e.target.value })} />
          </Field>
          {isRevision && (
            <Field label="Vì sao đối soát lại (bắt buộc)" span>
              <input className="input" required maxLength={255} value={form.revisionReason} onChange={(e) => setForm({ ...form, revisionReason: e.target.value })} />
            </Field>
          )}
        </div>
      </FormModal>
    </main>
  );
}
