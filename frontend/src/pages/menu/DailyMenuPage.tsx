import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';

import { ApiError, api } from '../../api/client';
import type { DailyMenuItem, DailyMenuResponse, FillQuotasResponse, MenuItem } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { ErrorNotice } from '../../components/ErrorNotice';
import { ReasonDialog } from '../../components/ReasonDialog';
import { DataTable, Field, FormModal, PageHead, StatusTag, num, useAction, Stepper } from '../../components/ui';
import { addDays, businessToday, dateTime, day, notOpened, vnd } from '../../lib/format';
import { DaySummary } from './DaySummary';

const STATUS: Record<string, [string, 'wait' | 'go' | 'ink']> = {
  DRAFT: ['Nháp: phục vụ chưa thấy', 'wait'],
  PUBLISHED: ['Đang bán', 'go'],
  CLOSED: ['Đã đóng', 'ink'],
};

const quotaText = (i: DailyMenuItem) => (i.quotaTotal == null ? '' : String(i.quotaTotal));

/**
 * Thực đơn theo ngày kinh doanh. Tạo từ danh mục: mọi món đang kinh doanh vào sẵn với số suất 0 (chưa mở bán);
 * quản lý nhập số suất cho món bán trong ngày ngay trên bảng, lưu, rồi công bố. Đóng xong xem tóm tắt cuối ngày.
 */
export function DailyMenuPage() {
  const { hasRole } = useAuth();
  const isManager = hasRole('MANAGER');
  const [date, setDate] = useState(businessToday());
  const [adding, setAdding] = useState(false);
  const [pick, setPick] = useState<number | ''>('');
  const [quota, setQuota] = useState('');
  const [drafts, setDrafts] = useState<Record<number, string>>({});
  const [hideNotOpened, setHideNotOpened] = useState(false);
  const [pausing, setPausing] = useState<DailyMenuItem | null>(null);

  const key = ['daily-menu', date];
  const menu = useQuery({
    queryKey: key,
    queryFn: () => api.get<DailyMenuResponse>(`/api/daily-menus?date=${date}`),
    retry: (n, e) => !(e instanceof ApiError && e.status === 404) && n < 2,
    refetchInterval: 15_000,
  });
  const items = useQuery({ queryKey: ['menu-items', 'active'], queryFn: () => api.get<MenuItem[]>('/api/menu-items'), enabled: isManager });
  const inv = [key, ['daily-menu-today'], ['ingredient-usage']];

  const notFound = menu.error instanceof ApiError && menu.error.status === 404;
  const m = menu.data;
  const editable = isManager && m != null && m.status !== 'CLOSED';
  const changes = editable ? m.items.filter((i) => i.dailyMenuItemId in drafts && drafts[i.dailyMenuItemId] !== quotaText(i)) : [];
  const anyOpened = m?.items.some((i) => !notOpened(i)) ?? false;

  const create = useAction(() => api.post('/api/daily-menus', { businessDate: date, fromCatalog: true }), {
    success: 'Đã tạo thực đơn từ danh mục món',
    invalidate: inv,
  });
  const status = useAction((a: 'publish' | 'close') => api.post(`/api/daily-menus/${m!.id}/${a}`), {
    success: (_r, a) => (a === 'publish' ? 'Đã công bố thực đơn' : 'Đã đóng thực đơn'),
    invalidate: inv,
  });
  const saveQuotas = useAction(
    () => api.put(`/api/daily-menus/${m!.id}/quotas`, {
      items: changes.map((i) => ({ dailyMenuItemId: i.dailyMenuItemId, quotaTotal: num(drafts[i.dailyMenuItemId]) })),
    }),
    { success: 'Đã lưu số suất', invalidate: inv, onSuccess: () => setDrafts({}) },
  );
  const fill = useAction(() => api.post<FillQuotasResponse>(`/api/daily-menus/${m!.id}/fill-quotas`), {
    success: (r) => `Đã điền số suất theo số đã bán ngày ${day(r.sourceDate)} (${r.updated} món)`,
    invalidate: inv,
    onSuccess: () => setDrafts({}),
  });
  const add = useAction(() => api.post(`/api/daily-menus/${m!.id}/items`, { menuItemId: pick, quotaTotal: num(quota) }), {
    success: 'Đã thêm món',
    invalidate: inv,
    onSuccess: () => {
      setPick('');
      setQuota('');
      setAdding(false);
    },
  });
  const remove = useAction((it: DailyMenuItem) => api.del(`/api/daily-menu-items/${it.dailyMenuItemId}`), { success: 'Đã bỏ món khỏi thực đơn', invalidate: inv });
  const pause = useAction(({ it, reason }: { it: DailyMenuItem; reason?: string }) =>
    api.post(`/api/daily-menu-items/${it.dailyMenuItemId}/${reason ? 'pause' : 'resume'}`, reason ? { reason } : undefined), {
    success: (_r, v) => (v.reason ? `Đã tạm dừng ${v.it.name}` : `Đã mở bán lại ${v.it.name}`),
    invalidate: inv,
    onSuccess: () => setPausing(null),
  });

  const onMenu = new Set(m?.items.map((i) => i.menuItemId));
  const candidates = (items.data ?? []).filter((i) => !onMenu.has(i.id));
  const rows = m && hideNotOpened ? m.items.filter((i) => !notOpened(i)) : m?.items;
  const changeDay = (d: string) => {
    setDrafts({});
    setDate(d);
  };

  return (
    <main className="main">
      <PageHead
        title="Thực đơn ngày"
        subtitle={m ? <>Ngày {day(m.businessDate)} · {m.items.length} món{m.publishedAt ? ` · công bố lúc ${dateTime(m.publishedAt)}` : ''}</> : `Ngày ${day(date)}`}
        actions={
          <>
            <Stepper unit="Ngày" onPrev={() => changeDay(addDays(date, -1))} onNext={() => changeDay(addDays(date, 1))}>
              <input className="input" type="date" aria-label="Ngày" value={date} onChange={(e) => e.target.value && changeDay(e.target.value)} />
            </Stepper>
            {date !== businessToday() && <button className="btn btn-sm" onClick={() => changeDay(businessToday())}>Hôm nay</button>}
          </>
        }
      />
      <ErrorNotice error={(!notFound && menu.error) || create.error || status.error || saveQuotas.error || fill.error || remove.error || pause.error} />

      {notFound && (
        <div className="empty stack" style={{ justifyItems: 'center' }}>
          <p>Chưa có thực đơn cho ngày {day(date)}.</p>
          {isManager && (
            <>
              <button className="btn btn-primary" disabled={create.isPending} onClick={() => create.mutate(undefined)}>
                Tạo thực đơn ngày {day(date)}
              </button>
              <p className="muted">Mọi món đang kinh doanh có công thức được đưa vào sẵn với số suất 0 (chưa mở bán).</p>
            </>
          )}
        </div>
      )}

      {m && (
        <>
          <div className="row-between" style={{ marginBottom: '1rem' }}>
            <div className="toolbar">
              <StatusTag tone={STATUS[m.status][1]}>{STATUS[m.status][0]}</StatusTag>
              <label className="check">
                <input type="checkbox" checked={hideNotOpened} onChange={(e) => setHideNotOpened(e.target.checked)} />
                Ẩn món chưa mở bán
              </label>
            </div>
            {isManager && (
              <div className="toolbar">
                {editable && <button className="btn" onClick={() => setAdding(true)}>Thêm món</button>}
                {m.status === 'DRAFT' && (
                  <button className="btn" disabled={fill.isPending} onClick={() => fill.mutate(undefined)}
                          title="Số suất mỗi món = số đã bán ở thực đơn gần nhất trước ngày này; món không có ở ngày đó giữ nguyên">
                    Điền theo số bán ngày trước
                  </button>
                )}
                {changes.length > 0 && (
                  <>
                    <button className="btn" onClick={() => setDrafts({})}>Bỏ thay đổi</button>
                    <button className="btn btn-primary" disabled={saveQuotas.isPending} onClick={() => saveQuotas.mutate(undefined)}>
                      Lưu số suất ({changes.length} món)
                    </button>
                  </>
                )}
                {m.status === 'DRAFT' && (
                  <button className="btn btn-primary" disabled={status.isPending || !anyOpened || changes.length > 0}
                          onClick={() => status.mutate('publish')}>
                    Công bố cho phục vụ
                  </button>
                )}
                {m.status === 'PUBLISHED' && (
                  <button className="btn" disabled={status.isPending} onClick={() => status.mutate('close')}>Đóng thực đơn</button>
                )}
              </div>
            )}
          </div>
          {isManager && m.status === 'DRAFT' && !anyOpened && (
            <div className="notice" style={{ marginBottom: '1rem' }}>
              Chưa mở bán món nào. Nhập số suất cho món bán trong ngày (để trống = không giới hạn) hoặc bấm "Điền theo số bán
              ngày trước", lưu rồi công bố.
            </div>
          )}
          {changes.length > 0 && (
            <div className="notice notice-warn" style={{ marginBottom: '1rem' }}>{changes.length} món đổi số suất chưa lưu.</div>
          )}
          <DataTable
            rows={rows}
            rowKey={(i) => i.dailyMenuItemId}
            empty={isManager ? 'Chưa có món. Bấm "Thêm món" để chọn từ danh mục.' : 'Chưa có món.'}
            columns={[
              { header: 'Món', render: (i) => <><b>{i.name}</b><div className="muted">{i.categoryName}</div></> },
              { header: 'Giá', render: (i) => vnd(i.price), align: 'right' },
              {
                header: 'Hạn mức',
                align: 'right',
                render: (i) =>
                  editable ? (
                    <input className="input" type="number" min={i.quotaUsed} inputMode="numeric" placeholder="Không giới hạn"
                           aria-label={`Số suất ${i.name}`} style={{ width: '8.5rem', textAlign: 'right' }}
                           value={drafts[i.dailyMenuItemId] ?? quotaText(i)}
                           onChange={(e) => setDrafts({ ...drafts, [i.dailyMenuItemId]: e.target.value })} />
                  ) : i.quotaTotal == null ? 'Không giới hạn' : i.quotaTotal,
              },
              { header: 'Đã bán', render: (i) => i.quotaUsed, align: 'right' },
              { header: 'Còn', render: (i) => (i.remaining == null ? '—' : i.remaining), align: 'right' },
              {
                header: 'Trạng thái',
                render: (i) =>
                  i.paused ? (
                    <><StatusTag tone="stop">Tạm dừng</StatusTag><div className="muted">{i.pauseReason}</div></>
                  ) : notOpened(i) ? (
                    <StatusTag tone="wait">Chưa mở bán</StatusTag>
                  ) : i.remaining != null && i.remaining <= 0 ? (
                    <StatusTag tone="stop">Hết suất</StatusTag>
                  ) : (
                    <StatusTag tone="go">Đang bán</StatusTag>
                  ),
              },
              {
                header: '',
                render: (i) => (
                  <div className="toolbar">
                    {m.status !== 'CLOSED' && !notOpened(i) &&
                      (i.paused ? (
                        <button className="btn btn-sm" onClick={() => pause.mutate({ it: i })}>Mở bán</button>
                      ) : (
                        <button className="btn btn-sm" onClick={() => setPausing(i)}>Tạm dừng</button>
                      ))}
                    {editable && i.quotaUsed === 0 && (
                      <button className="btn btn-sm btn-danger" onClick={() => remove.mutate(i)}>Bỏ</button>
                    )}
                  </div>
                ),
              },
            ]}
          />
          {isManager && m.status !== 'DRAFT' && <DaySummary menu={m} />}
        </>
      )}

      <FormModal open={adding} title="Thêm món vào thực đơn" submitLabel="Thêm món" busy={add.isPending} error={add.error}
                 onSubmit={() => add.mutate(undefined)} onClose={() => setAdding(false)}
                 description="Món cần có công thức hiện hành. Món mới thêm vào danh mục sau khi tạo thực đơn thì thêm ở đây.">
        <div className="form-grid">
          <Field label="Món" span>
            <select className="input" required value={pick} onChange={(e) => setPick(e.target.value ? Number(e.target.value) : '')}>
              <option value="">Chọn món…</option>
              {candidates.map((i) => (
                <option key={i.id} value={i.id} disabled={i.currentRecipeVersion == null}>
                  {i.name} · {vnd(i.price)}{i.currentRecipeVersion == null ? ' (chưa có công thức)' : ''}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Số suất" hint="Để trống = không giới hạn; 0 = chưa mở bán.">
            <input className="input" type="number" min={0} value={quota} onChange={(e) => setQuota(e.target.value)} />
          </Field>
        </div>
      </FormModal>

      <ReasonDialog open={pausing != null} title={`Tạm dừng ${pausing?.name ?? ''}?`}
                    description="Phục vụ không gọi được món này; món đang chờ bếp nhận sẽ bị từ chối." confirmLabel="Tạm dừng" danger
                    onConfirm={(reason) => pausing && pause.mutate({ it: pausing, reason })} onClose={() => setPausing(null)} />
    </main>
  );
}
