import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';

import { api } from '../../api/client';
import type { ExpenseByCategory, RevenueByDay, RevenueByItem, UnpaidClosure } from '../../api/types';
import { ErrorNotice } from '../../components/ErrorNotice';
import { DataTable, Field, PageHead } from '../../components/ui';
import { addDays, businessToday, day, expenseCategoryLabel, vnd } from '../../lib/format';

/** Doanh thu thực thu (chỉ từ thanh toán), món bán chạy, hóa đơn không thu, chi theo nhóm. */
export function ReportsPage() {
  const today = businessToday();
  const [from, setFrom] = useState(addDays(today, -6));
  const [to, setTo] = useState(today);
  const q = `from=${from}&to=${to}`;

  const byDay = useQuery({ queryKey: ['rev-day', q], queryFn: () => api.get<RevenueByDay[]>(`/api/reports/revenue-by-day?${q}`) });
  const byItem = useQuery({ queryKey: ['rev-item', q], queryFn: () => api.get<RevenueByItem[]>(`/api/reports/revenue-by-item?${q}`) });
  const unpaid = useQuery({ queryKey: ['unpaid', q], queryFn: () => api.get<UnpaidClosure[]>(`/api/reports/unpaid-closures?${q}`) });
  const expenses = useQuery({ queryKey: ['expense-report', q], queryFn: () => api.get<ExpenseByCategory[]>(`/api/reports/expenses-by-category?${q}`) });

  const revenue = (byDay.data ?? []).reduce((s, r) => s + r.totalAmount, 0);
  const cash = (byDay.data ?? []).reduce((s, r) => s + r.cashAmount, 0);
  const qr = (byDay.data ?? []).reduce((s, r) => s + r.qrAmount, 0);
  const bills = (byDay.data ?? []).reduce((s, r) => s + r.paidInvoices, 0);
  const spent = (expenses.data ?? []).reduce((s, r) => s + r.amount, 0);
  const maxDay = Math.max(1, ...(byDay.data ?? []).map((r) => r.totalAmount));
  const maxItem = Math.max(1, ...(byItem.data ?? []).map((r) => r.revenue));

  function preset(days: number) {
    setTo(today);
    setFrom(addDays(today, -(days - 1)));
  }

  return (
    <main className="main">
      <PageHead title="Báo cáo" subtitle={`${day(from)} – ${day(to)} · theo ngày kinh doanh (mốc 4 giờ sáng)`} />
      <div className="filters">
        <Field label="Từ ngày"><input className="input" type="date" value={from} max={to} onChange={(e) => e.target.value && setFrom(e.target.value)} /></Field>
        <Field label="Đến ngày"><input className="input" type="date" value={to} min={from} onChange={(e) => e.target.value && setTo(e.target.value)} /></Field>
        <div className="toolbar">
          <button className="btn btn-sm" onClick={() => preset(1)}>Hôm nay</button>
          <button className="btn btn-sm" onClick={() => preset(7)}>7 ngày</button>
          <button className="btn btn-sm" onClick={() => preset(30)}>30 ngày</button>
        </div>
      </div>
      <ErrorNotice error={byDay.error ?? byItem.error ?? unpaid.error ?? expenses.error} />

      <div className="stats">
        <div className="stat"><div className="label">Doanh thu thực thu</div><div className="value num">{vnd(revenue)}</div></div>
        <div className="stat"><div className="label">Tiền mặt</div><div className="value num">{vnd(cash)}</div></div>
        <div className="stat"><div className="label">Chuyển khoản QR</div><div className="value num">{vnd(qr)}</div></div>
        <div className="stat"><div className="label">Hóa đơn đã thu</div><div className="value num">{bills}</div></div>
        <div className="stat"><div className="label">Chi phí đã trả</div><div className="value num">{vnd(spent)}</div></div>
      </div>

      <div className="split split-even">
        <section className="stack">
          <h2>Doanh thu theo ngày</h2>
          <DataTable rows={byDay.data} rowKey={(r) => r.businessDate} empty="Chưa có khoản thu nào trong kỳ."
            columns={[
              { header: 'Ngày', render: (r) => day(r.businessDate), width: '7rem' },
              { header: 'Doanh thu', render: (r) => <div className="stack" style={{ gap: '0.25rem' }}><span className="num"><b>{vnd(r.totalAmount)}</b> · {r.paidInvoices} hóa đơn</span><div className="bar" style={{ width: `${(r.totalAmount / maxDay) * 100}%` }} /></div> },
              { header: 'Tiền mặt', render: (r) => vnd(r.cashAmount), align: 'right' },
              { header: 'QR', render: (r) => vnd(r.qrAmount), align: 'right' },
            ]} />
          <h2>Hóa đơn không thu tiền</h2>
          <DataTable rows={unpaid.data} rowKey={(r) => `${r.businessDate}-${r.status}`} empty="Không có hóa đơn khách bỏ về hay miễn thu."
            columns={[
              { header: 'Ngày', render: (r) => day(r.businessDate) },
              { header: 'Loại', render: (r) => (r.status === 'WAIVED' ? 'Miễn thu' : 'Khách bỏ về') },
              { header: 'Số hóa đơn', render: (r) => r.invoiceCount, align: 'right' },
              { header: 'Giá trị', render: (r) => vnd(r.amount), align: 'right' },
            ]} />
        </section>
        <section className="stack">
          <h2>Món bán được</h2>
          <DataTable rows={byItem.data} rowKey={(r) => r.menuItemId} empty="Chưa bán món nào trong kỳ."
            columns={[
              { header: 'Món', render: (r) => <div className="stack" style={{ gap: '0.25rem' }}><b>{r.itemName}</b><div className="bar" style={{ width: `${(r.revenue / maxItem) * 100}%`, background: 'var(--turmeric)' }} /></div> },
              { header: 'Suất', render: (r) => r.servings, align: 'right' },
              { header: 'Doanh thu', render: (r) => vnd(r.revenue), align: 'right' },
            ]} />
          <h2>Chi phí theo nhóm</h2>
          <DataTable rows={expenses.data} rowKey={(r) => r.category} empty="Chưa có khoản chi trong kỳ."
            columns={[
              { header: 'Nhóm', render: (r) => expenseCategoryLabel[r.category] ?? r.category },
              { header: 'Số khoản', render: (r) => r.expenseCount, align: 'right' },
              { header: 'Số tiền', render: (r) => vnd(r.amount), align: 'right' },
            ]} />
        </section>
      </div>
      <p className="muted" style={{ marginTop: '1rem' }}>Doanh thu chỉ tính tiền đã thu thật; khách bỏ về và miễn thu liệt kê riêng. Chênh lệch thu − chi là dòng tiền, không phải lợi nhuận.</p>
    </main>
  );
}
