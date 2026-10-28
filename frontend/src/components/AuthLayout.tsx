import { useState, type FormEvent, type ReactNode, type Ref } from 'react';

/** Bàn tròn nhìn từ trên, bốn ghế: dấu hiệu nhận diện của Atlas Restaurant. */
export function TableMark({ className = 'auth-mark' }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 48 48" aria-hidden="true">
      <circle cx="24" cy="24" r="12" />
      <rect x="16" y="3" width="16" height="6" rx="3" />
      <rect x="16" y="39" width="16" height="6" rx="3" />
      <rect x="3" y="16" width="6" height="16" rx="3" />
      <rect x="39" y="16" width="6" height="16" rx="3" />
    </svg>
  );
}

/** Khung chung cho đăng nhập / đổi mật khẩu: nền mực, form là tấm giấy (trên điện thoại nằm sát đáy, dễ với tay). */
export function AuthLayout({ title, lead, onSubmit, children, foot }: {
  title: string;
  lead?: ReactNode;
  onSubmit: (e: FormEvent) => void;
  children: ReactNode;
  foot?: ReactNode;
}) {
  return (
    <main className="auth-page">
      <div className="auth-brand">
        <TableMark />
        <span>Atlas Restaurant</span>
      </div>
      <form className="auth-sheet" onSubmit={onSubmit}>
        <div className="auth-head">
          <h1>{title}</h1>
          {lead && <p className="muted">{lead}</p>}
        </div>
        {children}
        {foot && <div className="auth-foot">{foot}</div>}
      </form>
    </main>
  );
}

/** Ô mật khẩu có nút hiện/ẩn và cảnh báo Caps Lock. */
export function PasswordInput({ id, label, value, onChange, autoComplete, show, onToggle, inputRef, ...rest }: {
  id: string;
  label: string;
  value: string;
  onChange: (v: string) => void;
  autoComplete: string;
  show: boolean;
  onToggle: () => void;
  inputRef?: Ref<HTMLInputElement>;
  minLength?: number;
  maxLength?: number;
  autoFocus?: boolean;
  'aria-invalid'?: boolean;
}) {
  const [capsLock, setCapsLock] = useState(false);
  const hint = capsLock ? `${id}-caps` : undefined;
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <div className="pw">
        <input id={id} ref={inputRef} className="input" type={show ? 'text' : 'password'} autoComplete={autoComplete} required
               value={value} onChange={(e) => onChange(e.target.value)} aria-describedby={hint}
               onKeyUp={(e) => setCapsLock(e.getModifierState('CapsLock'))}
               onBlur={() => setCapsLock(false)} {...rest} />
        <button type="button" className="pw-toggle" onClick={onToggle} aria-pressed={show}
                aria-label={show ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}>
          {show ? 'Ẩn' : 'Hiện'}
        </button>
      </div>
      {capsLock && <span id={hint} className="caps">Đang bật Caps Lock</span>}
    </div>
  );
}
