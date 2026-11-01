import { useQuery } from '@tanstack/react-query';

import { api } from '../../api/client';
import type { DailyMenuItem, DailyMenuResponse, IngredientUsageRow } from '../../api/types';
import { ErrorNotice } from '../../components/ErrorNotice';
import { DataTable, StatusTag } from '../../components/ui';
import { amount, notOpened } from '../../lib/format';

/** Món bán dưới tỷ lệ này của hạn mức được đánh dấu "dư nhiều". */
const LOW_SELL = 0.7;

function verdict(i: DailyMenuItem) {
  if (!i.quotaTotal) return null;
  if (i.quotaUsed >= i.quotaTotal) return <StatusTag tone="go">Bán hết</StatusTag>;
  if (i.quotaUsed / i.quotaTotal < LOW_SELL) return <StatusTag tone="wait">Dư nhiều</StatusTag>;
  return null;
}

/** Tóm tắt của thực đơn ngày: món bán so với hạn mức; nguyên liệu dự kiến, đã dùng khi nấu và tồn bếp. */
export function DaySummary({ menu }: { menu: DailyMenuResponse }) {
  const live = menu.status === 'PUBLISHED';
  const usage = useQuery({
    queryKey: ['ingredient-usage', menu.id],
    queryFn: () => api.get<IngredientUsageRow[]>(`/api/daily-menus/${menu.id}/ingredient-usage`),
    refetchInterval: live ? 30_000 : false,
  });
  const opened = menu.items.filter((i) => !notOpened(i));
  const skipped = menu.items.length - opened.length;
  const stockLabel = live ? 'Tồn bếp hiện tại' : 'Tồn bếp cuối ngày';

  return (
    <section className="stack" style={{ marginTop: '2rem' }}>
      <h2>{live ? 'Tóm tắt trong ngày (tính đến hiện tại)' : 'Tóm tắt cuối ngày'}</h2>
      <DataTable
        rows={opened}
        rowKey={(i) => i.dailyMenuItemId}
        empty="Không có món nào được mở bán."
        columns={[
          { header: 'Món', render: (i) => <b>{i.name}</b> },
          { header: 'Hạn mức', render: (i) => (i.quotaTotal == null ? 'Không giới hạn' : i.quotaTotal), align: 'right' },
          { header: 'Đã bán', render: (i) => i.quotaUsed, align: 'right' },
          { header: 'Dư', render: (i) => (i.remaining == null ? '—' : i.remaining), align: 'right' },
          { header: 'Tỷ lệ bán', render: (i) => (i.quotaTotal ? `${Math.round((i.quotaUsed / i.quotaTotal) * 100)}%` : '—'), align: 'right' },
          { header: 'Nhận xét', render: verdict },
        ]}
      />
      {skipped > 0 && <p className="muted">{skipped} món trong danh mục không mở bán ngày này.</p>}

      <h3>Nguyên liệu bếp</h3>
      <ErrorNotice error={usage.error} />
      <DataTable
        rows={usage.data}
        rowKey={(r) => r.kitchenIngredientTypeId}
        empty="Các món đã mở bán chưa có nguyên liệu trong công thức."
        columns={[
          { header: 'Nguyên liệu bếp', render: (r) => <b>{r.typeName}</b> },
          { header: 'Dự kiến theo hạn mức', render: (r) => (r.planned == null ? 'Có món không giới hạn' : amount(r.planned, r.baseUnit)), align: 'right' },
          { header: 'Đã dùng khi nấu', render: (r) => amount(r.used, r.baseUnit), align: 'right' },
          { header: 'Chưa dùng theo kế hoạch', render: (r) => (r.planned == null ? '—' : amount(r.planned - r.used, r.baseUnit)), align: 'right' },
          { header: stockLabel, render: (r) => amount(r.onHandAtEnd, r.baseUnit), align: 'right' },
        ]}
      />
      <p className="muted">
        Dự kiến = hạn mức × định lượng mỗi suất, cộng mọi món dùng chung nguyên liệu. Chưa dùng theo kế hoạch là phần đã
        chuẩn bị dư so với số bán, không phải lượng còn trong kho. {stockLabel} tính từ sổ kho
        {live ? '' : ' tới 04:00 sáng hôm sau'}, gồm cả hàng sắp hết hạn; xử lý phần dư ở Kho → Hoàn trả, hủy hàng hoặc Kiểm kê.
      </p>
    </section>
  );
}
