import { useQueries, useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';

import { api, ApiError } from '../api/client';
import type {
  DailyMenuResponse,
  OrderItem,
  RawStockRow,
  RevenueByDay,
  RevenueByItem,
  SessionResponse,
  TableResponse,
  UnusableLotRow,
} from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { ErrorNotice } from '../components/ErrorNotice';
import { Icon, type IconName } from '../components/Icon';
import { businessToday, day, minutesSince, vnd } from '../lib/format';

/** Món chưa xong quá ngưỡng này (phút) là trễ, giống màu đỏ ở màn bếp. */
const LATE_MINUTES = 15;

type Task = { tone: 'red' | 'amber' | 'blue' | 'violet'; icon: IconName; title: string; sub: string; to: string; action: string };

function greeting(now: Date): string {
  const h = Number(now.toLocaleString('en-GB', { hour: '2-digit', hour12: false, timeZone: 'Asia/Ho_Chi_Minh' }));
  return h < 11 ? 'Chào buổi sáng' : h < 14 ? 'Chào buổi trưa' : h < 18 ? 'Chào buổi chiều' : 'Chào buổi tối';
}

/** Tổng quan hôm nay cho Quản lý: số chính, việc cần xử lý, món bán so với hạn mức. Chỉ đọc, tự cập nhật. */
export function DashboardPage() {
  const { user } = useAuth();
  const today = businessToday();
  const now = Date.now();
  const q = `from=${today}&to=${today}`;

  const revenue = useQuery({ queryKey: ['rev-day', q], queryFn: () => api.get<RevenueByDay[]>(`/api/reports/revenue-by-day?${q}`), refetchInterval: 30_000 });
  const topItems = useQuery({ queryKey: ['rev-item', q], queryFn: () => api.get<RevenueByItem[]>(`/api/reports/revenue-by-item?${q}`), refetchInterval: 30_000 });
  const tables = useQuery({ queryKey: ['tables'], queryFn: () => api.get<TableResponse[]>('/api/tables'), refetchInterval: 15_000 });
  const queue = useQuery({ queryKey: ['kitchen-queue'], queryFn: () => api.get<OrderItem[]>('/api/kitchen/queue'), refetchInterval: 15_000 });
  const menu = useQuery({
    // Khóa riêng: ở đây 404 (chưa có thực đơn) thành null, còn màn gọi món coi 404 là lỗi
    queryKey: ['daily-menu-today', 'dashboard'],
    queryFn: async () => {
      try {
        return await api.get<DailyMenuResponse>('/api/daily-menus/today');
      } catch (e) {
        if (e instanceof ApiError && e.status === 404) return null; // chưa tạo thực đơn hôm nay
        throw e;
      }
    },
    refetchInterval: 30_000,
  });
  const rawStock = useQuery({ queryKey: ['raw-stock'], queryFn: () => api.get<RawStockRow[]>('/api/inventory/raw-stock'), refetchInterval: 60_000 });
  const unusable = useQuery({ queryKey: ['unusable-lots'], queryFn: () => api.get<UnusableLotRow[]>('/api/inventory/unusable-lots'), refetchInterval: 60_000 });

  const busyTables = (tables.data ?? []).filter((t) => t.currentSessionId != null);
  const sessions = useQueries({
    queries: busyTables.map((t) => ({
      queryKey: ['session', t.currentSessionId],
      queryFn: () => api.get<SessionResponse>(`/api/sessions/${t.currentSessionId}`),
      refetchInterval: 15_000,
    })),
  });
  const waitingPayment = sessions.filter((s) => s.data?.invoiceStatus === 'PAYMENT_PENDING').map((s) => s.data!);

  const rev = revenue.data?.[0];
  const items = queue.data ?? [];
  const doing = items.filter((i) => i.status !== 'READY');
  const late = doing.filter((i) => minutesSince(i.submittedAt, now) >= LATE_MINUTES);
  const ready = items.filter((i) => i.status === 'READY');
  const belowMin = (rawStock.data ?? []).filter((r) => r.belowMinimum);
  const lots = unusable.data ?? [];
  const menuItems = menu.data?.items ?? [];
  const soldOut = menuItems.filter((i) => i.quotaTotal != null && i.quotaTotal > 0 && (i.remaining ?? 1) <= 0);

  const tasks: Task[] = [];
  if (menu.isSuccess && (menu.data == null || menu.data.status === 'DRAFT')) {
    tasks.push({ tone: 'blue', icon: 'menu', title: menu.data == null ? 'Chưa có thực đơn hôm nay' : 'Thực đơn hôm nay chưa công bố',
      sub: 'Phục vụ chưa gọi món được cho đến khi công bố', to: '/menu/daily', action: 'Mở thực đơn' });
  }
  if (late.length > 0) {
    tasks.push({ tone: 'red', icon: 'clock', title: `${late.length} món chờ quá ${LATE_MINUTES} phút`,
      sub: [...new Set(late.map((i) => i.tableCode))].map((c) => `Bàn ${c}`).join(' · '), to: '/kitchen', action: 'Xem bếp' });
  }
  if (ready.length > 0) {
    tasks.push({ tone: 'amber', icon: 'bell', title: `${ready.length} món đã xong, chờ bưng`,
      sub: [...new Set(ready.map((i) => i.tableCode))].map((c) => `Bàn ${c}`).join(' · '), to: '/tables', action: 'Xem bàn' });
  }
  if (waitingPayment.length > 0) {
    tasks.push({ tone: 'violet', icon: 'finance', title: `${waitingPayment.length} bàn chờ thanh toán`,
      sub: waitingPayment.map((s) => `${s.tableCode} · ${vnd(s.invoiceTotal)}`).join(' · '), to: '/tables', action: 'Xem bàn' });
  }
  if (belowMin.length > 0) {
    tasks.push({ tone: 'red', icon: 'stock', title: `${belowMin.length} nguyên liệu dưới mức tối thiểu`,
      sub: belowMin.slice(0, 4).map((r) => r.ingredientName).join(', ') + (belowMin.length > 4 ? '…' : ''), to: '/inventory/stock', action: 'Xem kho' });
  }
  if (lots.length > 0) {
    tasks.push({ tone: 'amber', icon: 'alert', title: `${lots.length} lô hỏng hoặc hết hạn chưa xử lý`,
      sub: lots.slice(0, 3).map((l) => l.itemName).join(', ') + (lots.length > 3 ? '…' : ''), to: '/inventory/returns', action: 'Hoàn trả, hủy' });
  }
  if (soldOut.length > 0) {
    tasks.push({ tone: 'amber', icon: 'menu', title: `${soldOut.length} món đã hết suất`,
      sub: soldOut.map((i) => i.name).join(', '), to: '/menu/daily', action: 'Sửa hạn mức' });
  }

  const quotaRows = menuItems
    .filter((i) => i.quotaTotal != null && i.quotaTotal > 0)
    .sort((a, b) => b.quotaUsed / (b.quotaTotal ?? 1) - a.quotaUsed / (a.quotaTotal ?? 1));
  const top = (topItems.data ?? []).slice().sort((a, b) => b.revenue - a.revenue).slice(0, 6);
  const topMax = Math.max(1, ...top.map((t) => t.revenue));
  const name = user?.fullName?.trim().split(/\s+/).pop() ?? user?.username ?? '';

  return (
    <main className="main dashboard">
      <div className="page-head">
        <div>
          <p className="muted">Ngày kinh doanh {day(today)} · tính từ 04:00</p>
          <h1>{greeting(new Date())}, {name}</h1>
        </div>
        <div className="toolbar">
          <Link className="btn" to="/menu/daily">Thực đơn hôm nay</Link>
          <Link className="btn" to="/finance/reports">Báo cáo đầy đủ</Link>
        </div>
      </div>

      <ErrorNotice error={revenue.error ?? tables.error ?? queue.error ?? menu.error} />

      <div className="kpis">
        <Link className="kpi" to="/finance/reports">
          <span className="label">Doanh thu hôm nay</span>
          <span className="value num">{vnd(rev?.totalAmount ?? 0)}</span>
          <span className="note">{rev ? `${rev.paidInvoices} hóa đơn · tiền mặt ${vnd(rev.cashAmount)} · QR ${vnd(rev.qrAmount)}` : 'Chưa có hóa đơn nào'}</span>
        </Link>
        <Link className="kpi" to="/tables">
          <span className="label">Bàn đang có khách</span>
          <span className="value num">{busyTables.length}/{tables.data?.length ?? 0}</span>
          <span className="note">{waitingPayment.length > 0 ? `${waitingPayment.length} bàn chờ thanh toán` : 'Không bàn nào chờ thanh toán'}</span>
        </Link>
        <Link className="kpi" to="/kitchen">
          <span className="label">Món bếp đang làm</span>
          <span className="value num">{doing.reduce((s, i) => s + i.quantity, 0)}</span>
          <span className="note" data-tone={late.length > 0 ? 'red' : undefined}>
            {late.length > 0 ? `${late.length} món quá ${LATE_MINUTES} phút` : 'Không món nào trễ'}
          </span>
        </Link>
        <Link className="kpi" to="/menu/daily">
          <span className="label">Thực đơn hôm nay</span>
          <span className="value">{menu.data == null ? 'Chưa có' : menu.data.status === 'PUBLISHED' ? 'Đang bán' : menu.data.status === 'DRAFT' ? 'Bản nháp' : 'Đã đóng'}</span>
          <span className="note">{menuItems.length} món · {soldOut.length} món hết suất</span>
        </Link>
      </div>

      <div className="dash-grid">
        <section className="panel" aria-labelledby="tasks-h">
          <div className="row-between">
            <h2 id="tasks-h">Việc cần xử lý</h2>
            <span className="chip">{tasks.length} việc</span>
          </div>
          {tasks.length === 0 ? (
            <p className="muted">Không có việc nào cần xử lý.</p>
          ) : (
            <ul className="tasks">
              {tasks.map((t) => (
                <li key={t.title} data-tone={t.tone}>
                  <span className="task-icon"><Icon name={t.icon} /></span>
                  <span className="task-text">
                    <b>{t.title}</b>
                    <span className="muted">{t.sub}</span>
                  </span>
                  <Link to={t.to}>{t.action}</Link>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="panel" aria-labelledby="top-h">
          <div className="row-between">
            <h2 id="top-h">Bán chạy hôm nay</h2>
            <span className="muted">theo doanh thu đã thu</span>
          </div>
          {top.length === 0 ? (
            <p className="muted">Chưa có hóa đơn đã thu hôm nay.</p>
          ) : (
            <ul className="meters">
              {top.map((t) => (
                <li key={t.menuItemId}>
                  <div className="row-between">
                    <span>{t.itemName}</span>
                    <span className="num muted">{t.servings} suất · {vnd(t.revenue)}</span>
                  </div>
                  <div className="meter" aria-hidden="true"><span style={{ width: `${(100 * t.revenue) / topMax}%` }} /></div>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      <section className="panel" aria-labelledby="quota-h">
        <div className="row-between">
          <h2 id="quota-h">Món bán hôm nay so với hạn mức</h2>
          <Link to="/menu/daily">Sửa hạn mức</Link>
        </div>
        {quotaRows.length === 0 ? (
          <p className="muted">Không có món nào đặt hạn mức suất hôm nay.</p>
        ) : (
          <ul className="meters meters-grid">
            {quotaRows.map((i) => {
              const pct = Math.min(100, (100 * i.quotaUsed) / (i.quotaTotal ?? 1));
              return (
                <li key={i.dailyMenuItemId}>
                  <div className="row-between">
                    <span>{i.name}</span>
                    <span className="num muted">{i.quotaUsed}/{i.quotaTotal} suất</span>
                  </div>
                  <div className="meter" data-tone={pct >= 100 ? 'red' : pct >= 80 ? 'amber' : undefined} aria-hidden="true">
                    <span style={{ width: `${pct}%` }} />
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </section>
    </main>
  );
}
