import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';

import { api } from '../../api/client';
import type { TableResponse } from '../../api/types';
import { ErrorNotice } from '../../components/ErrorNotice';
import { DataTable, Field, FormModal, PageHead, StatusTag, num, useAction } from '../../components/ui';

/** Thêm, sửa, ngừng dùng bàn. Chỉ ngừng được bàn đang trống. */
export function TablesAdminPage() {
  const [editing, setEditing] = useState<TableResponse | 'new' | null>(null);
  const [code, setCode] = useState('');
  const [capacity, setCapacity] = useState('4');

  const tables = useQuery({ queryKey: ['tables', 'all'], queryFn: () => api.get<TableResponse[]>('/api/tables?includeInactive=true') });
  const save = useAction(
    () => {
      const body = { code: code.trim(), capacity: num(capacity) };
      return editing === 'new' ? api.post('/api/tables', body) : api.put(`/api/tables/${(editing as TableResponse).id}`, body);
    },
    { success: 'Đã lưu bàn', invalidate: [['tables']], onSuccess: () => setEditing(null) },
  );
  const toggle = useAction((t: TableResponse) => api.post(`/api/tables/${t.id}/${t.active ? 'deactivate' : 'activate'}`), {
    success: (_r, t) => (t.active ? `Đã ngừng dùng bàn ${t.code}` : `Đã dùng lại bàn ${t.code}`),
    invalidate: [['tables']],
  });

  function open(t: TableResponse | 'new') {
    setCode(t === 'new' ? '' : t.code);
    setCapacity(t === 'new' ? '4' : String(t.capacity));
    save.reset();
    setEditing(t);
  }

  return (
    <main className="main">
      <PageHead
        title="Quản lý bàn"
        subtitle="Bàn ngừng dùng không mở được phiên mới. Chỉ ngừng được bàn đang trống."
        actions={<button className="btn btn-primary" onClick={() => open('new')}>Thêm bàn</button>}
      />
      <ErrorNotice error={tables.error ?? toggle.error} />
      <DataTable
        rows={tables.data}
        rowKey={(t) => t.id}
        empty="Chưa có bàn nào."
        columns={[
          { header: 'Mã bàn', render: (t) => <b>{t.code}</b> },
          { header: 'Số chỗ', render: (t) => t.capacity, align: 'right' },
          { header: 'Trạng thái', render: (t) => (t.status === 'OCCUPIED' ? <StatusTag tone="wait">Có khách</StatusTag> : <StatusTag tone="go">Trống</StatusTag>) },
          { header: 'Sử dụng', render: (t) => (t.active ? 'Đang dùng' : <StatusTag tone="stop">Ngừng dùng</StatusTag>) },
          {
            header: '',
            render: (t) => (
              <div className="toolbar">
                <button className="btn btn-sm" onClick={() => open(t)}>Sửa</button>
                <button className="btn btn-sm" disabled={toggle.isPending || (t.active && t.status === 'OCCUPIED')} onClick={() => toggle.mutate(t)}>
                  {t.active ? 'Ngừng dùng' : 'Dùng lại'}
                </button>
              </div>
            ),
          },
        ]}
      />
      <FormModal
        open={editing != null}
        title={editing === 'new' ? 'Thêm bàn' : `Sửa bàn ${editing?.code ?? ''}`}
        submitLabel="Lưu bàn"
        busy={save.isPending}
        error={save.error}
        onSubmit={() => save.mutate(undefined)}
        onClose={() => setEditing(null)}
      >
        <div className="form-grid">
          <Field label="Mã bàn">
            <input className="input" required maxLength={20} value={code} onChange={(e) => setCode(e.target.value)} autoFocus />
          </Field>
          <Field label="Số chỗ ngồi">
            <input className="input" type="number" min={1} max={100} required value={capacity} onChange={(e) => setCapacity(e.target.value)} />
          </Field>
        </div>
      </FormModal>
    </main>
  );
}
