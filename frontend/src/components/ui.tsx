import { useEffect, useRef, type FormEvent, type ReactNode } from 'react';
import { useMutation, useQueryClient, type QueryKey } from '@tanstack/react-query';

import { ErrorNotice } from './ErrorNotice';
import { useToast } from './Toast';

export function PageHead({ title, subtitle, actions }: { title: string; subtitle?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="page-head">
      <div>
        <h1>{title}</h1>
        {subtitle && <p className="muted">{subtitle}</p>}
      </div>
      {actions && <div className="toolbar">{actions}</div>}
    </div>
  );
}

/** Hộp thoại chứa form; Enter = gửi. Lỗi từ backend hiện ngay trong hộp thoại. */
export function FormModal({
  open,
  title,
  description,
  submitLabel,
  busy,
  error,
  wide,
  danger,
  onSubmit,
  onClose,
  children,
}: {
  open: boolean;
  title: string;
  description?: ReactNode;
  submitLabel: string;
  busy?: boolean;
  error?: unknown;
  wide?: boolean;
  danger?: boolean;
  onSubmit: () => void;
  onClose: () => void;
  children?: ReactNode;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const d = ref.current;
    if (!d) return;
    if (open && !d.open) d.showModal();
    else if (!open && d.open) d.close();
  }, [open]);

  return (
    <dialog ref={ref} className={wide ? 'sheet sheet-wide' : 'sheet'} onClose={onClose}>
      {open && (
        <form
          method="dialog"
          onSubmit={(e: FormEvent) => {
            e.preventDefault();
            onSubmit();
          }}
        >
          <h2>{title}</h2>
          {description && <p className="muted">{description}</p>}
          {children}
          <ErrorNotice error={error} />
          <div className="sheet-actions">
            <button type="button" className="btn btn-quiet" onClick={onClose}>
              Quay lại
            </button>
            <button type="submit" className={danger ? 'btn btn-danger' : 'btn btn-primary'} disabled={busy}>
              {busy ? 'Đang lưu…' : submitLabel}
            </button>
          </div>
        </form>
      )}
    </dialog>
  );
}

export function Field({ label, hint, children, span }: { label: string; hint?: string; children: ReactNode; span?: boolean }) {
  return (
    <label className={span ? 'field span-2' : 'field'}>
      <span className="field-label">{label}</span>
      {children}
      {hint && <span className="hint">{hint}</span>}
    </label>
  );
}

export interface Column<T> {
  header: string;
  render: (row: T) => ReactNode;
  align?: 'right';
  width?: string;
}

export function DataTable<T>({
  rows,
  columns,
  rowKey,
  empty,
  onRowClick,
  selectedKey,
}: {
  rows: T[] | undefined;
  columns: Column<T>[];
  rowKey: (row: T) => string | number;
  empty: ReactNode;
  onRowClick?: (row: T) => void;
  selectedKey?: string | number | null;
}) {
  if (!rows) return <div className="center-screen small">Đang tải…</div>;
  if (rows.length === 0) return <div className="empty">{empty}</div>;
  return (
    <div className="table-wrap">
      <table className="data">
        <thead>
          <tr>
            {columns.map((c) => (
              <th key={c.header} className={c.align === 'right' ? 'r' : undefined} style={c.width ? { width: c.width } : undefined}>
                {c.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr
              key={rowKey(r)}
              className={onRowClick ? 'clickable' : undefined}
              aria-selected={selectedKey != null && selectedKey === rowKey(r)}
              onClick={onRowClick ? () => onRowClick(r) : undefined}
            >
              {columns.map((c) => (
                <td key={c.header} className={c.align === 'right' ? 'r num' : undefined}>
                  {c.render(r)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function Tabs<K extends string>({ value, options, onChange }: { value: K; options: [K, string][]; onChange: (k: K) => void }) {
  return (
    <div className="seg" role="tablist">
      {options.map(([k, label]) => (
        <button key={k} type="button" role="tab" aria-selected={value === k} onClick={() => onChange(k)}>
          {label}
        </button>
      ))}
    </div>
  );
}

/** Thao tác ghi: toast khi thành công + làm mới các truy vấn liên quan. */
export function useAction<V, R = unknown>(
  fn: (v: V) => Promise<R>,
  opts: { success?: string | ((r: R, v: V) => string); invalidate?: QueryKey[]; onSuccess?: (r: R, v: V) => void } = {},
) {
  const toast = useToast();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: (r, v) => {
      if (opts.success) toast(typeof opts.success === 'string' ? opts.success : opts.success(r, v));
      opts.invalidate?.forEach((k) => qc.invalidateQueries({ queryKey: k }));
      opts.onSuccess?.(r, v);
    },
  });
}

export function StatusTag({ tone, children }: { tone?: 'wait' | 'go' | 'stop' | 'ink'; children: ReactNode }) {
  return <span className={`chip ${tone ? 'chip-' + tone : ''}`}>{children}</span>;
}

/** Ô nhập số: chuỗi rỗng = null. */
export function num(v: string): number | null {
  if (v.trim() === '') return null;
  const n = Number(v.replace(',', '.'));
  return Number.isFinite(n) ? n : null;
}
