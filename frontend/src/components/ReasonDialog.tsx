import { useEffect, useRef, useState } from 'react';

interface Props {
  open: boolean;
  title: string;
  description?: string;
  confirmLabel: string;
  danger?: boolean;
  onConfirm: (reason: string) => void;
  onClose: () => void;
}

/** Thao tác bắt buộc có lý do (hủy món, từ chối, khách bỏ về…). DB cũng từ chối lý do trống. */
export function ReasonDialog({ open, title, description, confirmLabel, danger, onConfirm, onClose }: Props) {
  const ref = useRef<HTMLDialogElement>(null);
  const [reason, setReason] = useState('');

  useEffect(() => {
    const d = ref.current;
    if (!d) return;
    if (open && !d.open) {
      setReason('');
      d.showModal();
    } else if (!open && d.open) {
      d.close();
    }
  }, [open]);

  return (
    <dialog ref={ref} className="sheet" onClose={onClose}>
      <form
        method="dialog"
        onSubmit={(e) => {
          e.preventDefault();
          if (reason.trim()) onConfirm(reason.trim());
        }}
      >
        <h2>{title}</h2>
        {description && <p className="muted">{description}</p>}
        <div className="field">
          <label htmlFor="reason">Lý do</label>
          <textarea id="reason" className="input" value={reason} maxLength={200} required autoFocus
                    onChange={(e) => setReason(e.target.value)} />
        </div>
        <div className="sheet-actions">
          <button type="button" className="btn btn-quiet" onClick={onClose}>
            Quay lại
          </button>
          <button type="submit" className={danger ? 'btn btn-danger' : 'btn btn-primary'} disabled={!reason.trim()}>
            {confirmLabel}
          </button>
        </div>
      </form>
    </dialog>
  );
}
