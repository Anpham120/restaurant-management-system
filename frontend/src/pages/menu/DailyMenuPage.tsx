import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';

import { ApiError, api } from '../../api/client';
import type { DailyMenuItem, DailyMenuResponse, MenuItem } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { ErrorNotice } from '../../components/ErrorNotice';
import { ReasonDialog } from '../../components/ReasonDialog';
import { DataTable, Field, FormModal, PageHead, StatusTag, num, useAction } from '../../components/ui';
import { addDays, businessToday, dateTime, day, vnd } from '../../lib/format';

const STATUS: Record<string, [string, 'wait' | 'go' | 'ink']> = {
  DRAFT: ['Nháp: phục vụ chưa thấy', 'wait'],
  PUBLISHED: ['Đang bán', 'go'],
  CLOSED: ['Đã đóng', 'ink'],
};

/** Thực đơn theo ngày kinh doanh: chọn món, hạn mức suất, công bố, tạm dừng/mở bán. */
export function DailyMenuPage() {
  const { hasRole } = useAuth();
  const isManager = hasRole('MANAGER');
  const [date, setDate] = useState(businessToday());
  const [adding, setAdding] = useState(false);
  const [pick, setPick] = useState<number | ''>('');
  const [quota, setQuota] = useState('');
  const [editing, setEditing] = useState<DailyMenuItem | null>(null);
  const [pausing, setPausing] = useState<DailyMenuItem | null>(null);

  const key = ['daily-menu', date];
  const menu = useQuery({
    queryKey: key,
    queryFn: () => api.get<DailyMenuResponse>(`/api/daily-menus?date=${date}`),
    retry: (n, e) => !(e instanceof ApiError && e.status === 404) && n < 2,
    refetchInterval: 15_000,
  });
  const items = useQuery({ queryKey: ['menu-items', 'active'], queryFn: () => api.get<MenuItem[]>('/api/menu-items'), enabled: isManager });
  const inv = [key, ['daily-menu-today']];

  const create = useAction(() => api.post('/api/daily-menus', { businessDate: date }), { success: 'Đã tạo thực đơn', invalidate: inv });
  const status = useAction((a: 'publish' | 'close') => api.post(`/api/daily-menus/${menu.data!.id}/${a}`), {
    success: (_r, a) => (a === 'publish' ? 'Đã công bố thực đơn' : 'Đã đóng thực đơn'),
    invalidate: inv,
  });
  const add = useAction(() => api.post(`/api/daily-menus/${menu.data!.id}/items`, { menuItemId: pick, quotaTotal: num(quota) }), {
    success: 'Đã thêm món',
    invalidate: inv,
    onSuccess: () => {
      setPick('');
      setQuota('');
      setAdding(false);
    },
  });
  const setQuotaAction = useAction(() => api.put(`/api/daily-menu-items/${editing!.dailyMenuItemId}`, { quotaTotal: num(quota) }), {
    success: 'Đã đổi hạn mức',
    invalidate: inv,
    onSuccess: () => setEditing(null),
  });
  const remove = useAction((it: DailyMenuItem) => api.del(`/api/daily-menu-items/${it.dailyMenuItemId}`), { success: 'Đã bỏ món khỏi thực đơn', invalidate: inv });
  const pause = useAction(({ it, reason }: { it: DailyMenuItem; reason?: string }) =>
    api.post(`/api/daily-menu-items/${it.dailyMenuItemId}/${reason ? 'pause' : 'resume'}`, reason ? { reason } : undefined), {
    success: (_r, v) => (v.reason ? `Đã tạm dừng ${v.it.name}` : `Đã mở bán lại ${v.it.name}`),
    invalidate: inv,
    onSuccess: () => setPausing(null),
  });

  const notFound = menu.error instanceof ApiError && menu.error.status === 404;
  const m = menu.data;
  const onMenu = new Set(m?.items.map((i) => i.menuItemId));
  const candidates = (items.data ?? []).filter((i) => !onMenu.has(i.id));
  const editable = isManager && m && m.status !== 'CLOSED';

  return (
    <main className="main">
      <PageHead
        title="Thực đơn ngày"
        subtitle={m ? <>Ngày {day(m.businessDate)} · {m.items.length} món{m.publishedAt ? ` · công bố lúc ${dateTime(m.publishedAt)}` : ''}</> : `Ngày ${day(date)}`}
        actions={
          <>
            <button className="btn btn-sm" onClick={() => setDate(addDays(date, -1))}>Ngày trước</button>
            <input className="input" type="date" value={date} onChange={(e) => e.target.value && setDate(e.target.value)} style={{ width: 170 }} />
            <button className="btn btn-sm" onClick={() => setDate(addDays(date, 1))}>Ngày sau</button>
            {date !== businessToday() && <button className="btn btn-sm" onClick={() => setDate(businessToday())}>Hôm nay</button>}
          </>
        }
      />
      <ErrorNotice error={(!notFound && menu.error) || create.error || status.error || remove.error || pause.error} />

      {notFound && (
        <div className="empty stack" style={{ justifyItems: 'center' }}>
          <p>Chưa có thực đơn cho ngày {day(date)}.</p>
          {isManager && (
            <button className="btn btn-primary" disabled={create.isPending} onClick={() => create.mutate(undefined)}>
              Tạo thực đơn ngày {day(date)}
            </button>
          )}
        </div>
      )}

      {m && (
        <>
          <div className="row-between" style={{ marginBottom: '1rem' }}>
            <StatusTag tone={STATUS[m.status][1]}>{STATUS[m.status][0]}</StatusTag>
            {isManager && (
              <div className="toolbar">
                {editable && <button className="btn" onClick={() => setAdding(true)}>Thêm món</button>}
                {m.status === 'DRAFT' && (
                  <button className="btn btn-primary" disabled={status.isPending || m.items.length === 0} onClick={() => status.mutate('publish')}>
                    Công bố cho phục vụ
                  </button>
                )}
                {m.status === 'PUBLISHED' && (
                  <button className="btn" disabled={status.isPending} onClick={() => status.mutate('close')}>Đóng thực đơn</button>
                )}
              </div>
            )}
          </div>
          <DataTable
            rows={m.items}
            rowKey={(i) => i.dailyMenuItemId}
            empty={isManager ? 'Chưa có món. Bấm "Thêm món" để chọn từ danh mục.' : 'Chưa có món.'}
            columns={[
              { header: 'Món', render: (i) => <><b>{i.name}</b><div className="muted">{i.categoryName}</div></> },
              { header: 'Giá', render: (i) => vnd(i.price), align: 'right' },
              { header: 'Hạn mức', render: (i) => (i.quotaTotal == null ? 'Không giới hạn' : i.quotaTotal), align: 'right' },
              { header: 'Đã bán', render: (i) => i.quotaUsed, align: 'right' },
              { header: 'Còn', render: (i) => (i.remaining == null ? '—' : i.remaining), align: 'right' },
              {
                header: 'Trạng thái',
                render: (i) =>
                  i.paused ? (
                    <><StatusTag tone="stop">Tạm dừng</StatusTag><div className="muted">{i.pauseReason}</div></>
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
                    {m.status !== 'CLOSED' &&
                      (i.paused ? (
                        <button className="btn btn-sm" onClick={() => pause.mutate({ it: i })}>Mở bán</button>
                      ) : (
                        <button className="btn btn-sm" onClick={() => setPausing(i)}>Tạm dừng</button>
                      ))}
                    {editable && (
                      <button className="btn btn-sm" onClick={() => { setQuota(i.quotaTotal == null ? '' : String(i.quotaTotal)); setQuotaAction.reset(); setEditing(i); }}>
                        Hạn mức
                      </button>
                    )}
                    {editable && i.quotaUsed === 0 && (
                      <button className="btn btn-sm btn-danger" onClick={() => remove.mutate(i)}>Bỏ</button>
                    )}
                  </div>
                ),
              },
            ]}
          />
        </>
      )}

      <FormModal open={adding} title="Thêm món vào thực đơn" submitLabel="Thêm món" busy={add.isPending} error={add.error}
                 onSubmit={() => add.mutate(undefined)} onClose={() => setAdding(false)}
                 description="Món cần có công thức hiện hành. Hạn mức để trống nếu bán không giới hạn.">
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
          <Field label="Hạn mức suất" hint="Để trống = không giới hạn; 0 = hết suất.">
            <input className="input" type="number" min={0} value={quota} onChange={(e) => setQuota(e.target.value)} />
          </Field>
        </div>
      </FormModal>

      <FormModal open={editing != null} title={`Hạn mức: ${editing?.name ?? ''}`} submitLabel="Lưu hạn mức" busy={setQuotaAction.isPending}
                 error={setQuotaAction.error} onSubmit={() => setQuotaAction.mutate(undefined)} onClose={() => setEditing(null)}
                 description={editing ? `Đã bán ${editing.quotaUsed} suất; hạn mức không được thấp hơn số này.` : undefined}>
        <Field label="Hạn mức suất" hint="Để trống = không giới hạn.">
          <input className="input" type="number" min={editing?.quotaUsed ?? 0} value={quota} onChange={(e) => setQuota(e.target.value)} autoFocus />
        </Field>
      </FormModal>

      <ReasonDialog open={pausing != null} title={`Tạm dừng ${pausing?.name ?? ''}?`}
                    description="Phục vụ không gọi được món này; món đang chờ bếp nhận sẽ bị từ chối." confirmLabel="Tạm dừng" danger
                    onConfirm={(reason) => pausing && pause.mutate({ it: pausing, reason })} onClose={() => setPausing(null)} />
    </main>
  );
}
