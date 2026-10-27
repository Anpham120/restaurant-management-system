import { useEffect, useRef } from 'react';

interface Props {
  open: boolean;
  title: string;
  description?: string;
  confirmLabel: string;
  busy?: boolean;
  onConfirm: () => void;
  onClose: () => void;
}

export function ConfirmDialog({ open, title, description, confirmLabel, busy, onConfirm, onClose }: Props) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const d = ref.current;
    if (!d) return;
    if (open && !d.open) d.showModal();
    else if (!open && d.open) d.close();
  }, [open]);

  return (
    <dialog ref={ref} className="sheet" onClose={onClose}>
      <form
        method="dialog"
        onSubmit={(e) => {
          e.preventDefault();
          onConfirm();
        }}
      >
        <h2>{title}</h2>
        {description && <p className="muted">{description}</p>}
        <div className="sheet-actions">
          <button type="button" className="btn btn-quiet" onClick={onClose}>
            Quay lại
          </button>
          <button type="submit" className="btn btn-primary" disabled={busy} autoFocus>
            {confirmLabel}
          </button>
        </div>
      </form>
    </dialog>
  );
}
