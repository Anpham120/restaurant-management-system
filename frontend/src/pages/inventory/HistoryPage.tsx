import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';

import { api } from '../../api/client';
import type { PreparationYieldRow, StockHistoryRow } from '../../api/types';
import { ErrorNotice } from '../../components/ErrorNotice';
import { DataTable, Field, PageHead, Tabs } from '../../components/ui';
import { addDays, amount, businessToday, dateTime, day, fromLocalInput, movementTypeLabel } from '../../lib/format';
import { useIngredients } from './shared';

/** Sổ kho (mọi biến động) và hiệu suất sơ chế theo khoảng thời gian. */
export function HistoryPage() {
  const [tab, setTab] = useState<'history' | 'yield'>('history');
  const today = businessToday();
  const [from, setFrom] = useState(addDays(today, -6));
  const [to, setTo] = useState(today);
  const [ingredientId, setIngredientId] = useState('');
  const ings = useIngredients(false);

  // ngày kinh doanh bắt đầu 04:00 giờ Việt Nam
  const fromTs = fromLocalInput(`${from}T04:00`)!;
  const toTs = fromLocalInput(`${addDays(to, 1)}T04:00`)!;
  const history = useQuery({
    queryKey: ['stock-history', from, to, ingredientId],
    queryFn: () => api.get<StockHistoryRow[]>(`/api/inventory/history?from=${encodeURIComponent(fromTs)}&to=${encodeURIComponent(toTs)}${ingredientId ? `&ingredientId=${ingredientId}` : ''}`),
    enabled: tab === 'history',
  });
  const yields = useQuery({
    queryKey: ['prep-yield', from, to],
    queryFn: () => api.get<PreparationYieldRow[]>(`/api/inventory/preparation-yield?from=${from}&to=${to}`),
    enabled: tab === 'yield',
  });

  return (
    <main className="main">
      <PageHead title="Lịch sử kho" />
      <Tabs value={tab} onChange={setTab} options={[['history', 'Sổ kho'], ['yield', 'Hiệu suất sơ chế']]} />
      <div className="filters">
        <Field label="Từ ngày"><input className="input" type="date" value={from} max={to} onChange={(e) => e.target.value && setFrom(e.target.value)} /></Field>
        <Field label="Đến ngày"><input className="input" type="date" value={to} min={from} onChange={(e) => e.target.value && setTo(e.target.value)} /></Field>
        {tab === 'history' && (
          <Field label="Nguyên liệu">
            <select className="input" value={ingredientId} onChange={(e) => setIngredientId(e.target.value)}>
              <option value="">Tất cả</option>
              {ings.data?.map((g) => <option key={g.id} value={g.id}>{g.name}</option>)}
            </select>
          </Field>
        )}
      </div>
      <ErrorNotice error={history.error ?? yields.error} />
      {tab === 'history' ? (
        <DataTable rows={history.data} rowKey={(r) => r.movementId} empty="Không có biến động trong khoảng này."
          columns={[
            { header: 'Lúc', render: (r) => dateTime(r.createdAt) },
            { header: 'Loại', render: (r) => movementTypeLabel[r.movementType] ?? r.movementType },
            { header: 'Nơi', render: (r) => ({ RAW: 'Kho', WIP: 'Chờ sơ chế', KITCHEN: 'Bếp' })[r.location] ?? r.location },
            { header: 'Nguyên liệu', render: (r) => r.ingredientName },
            { header: 'Thay đổi', render: (r) => <span style={{ color: r.quantityDelta < 0 ? 'var(--chili)' : 'var(--jade)' }}>{r.quantityDelta > 0 ? '+' : ''}{amount(r.quantityDelta)}</span>, align: 'right' },
            { header: 'Chứng từ', render: (r) => r.reference ?? '—' },
            { header: 'Lý do', render: (r) => r.reason ?? '' },
          ]} />
      ) : (
        <DataTable rows={yields.data} rowKey={(r) => r.preparationId} empty="Không có phiếu sơ chế đã xác nhận trong khoảng này."
          columns={[
            { header: 'Ngày', render: (r) => day(r.businessDate) },
            { header: 'Nguyên liệu', render: (r) => <><b>{r.ingredientName}</b>{r.lotCode && <div className="muted">lô {r.lotCode}</div>}</> },
            { header: 'Người làm', render: (r) => r.preparedBy },
            { header: 'Đầu vào', render: (r) => amount(r.inputQuantity), align: 'right' },
            { header: 'Thành phẩm', render: (r) => amount(r.outputQuantity), align: 'right' },
            { header: 'Hao hụt', render: (r) => `${amount(r.lossQuantity)} (${Math.round(r.lossRatio * 100)}%)`, align: 'right' },
            { header: 'Lý do', render: (r) => r.lossReason ?? '' },
          ]} />
      )}
    </main>
  );
}
