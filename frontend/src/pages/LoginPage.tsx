import { useRef, useState, type FormEvent } from 'react';
import { Navigate, useNavigate } from 'react-router';

import { ApiError } from '../api/client';
import { AuthLayout, PasswordInput } from '../components/AuthLayout';
import { ErrorNotice } from '../components/ErrorNotice';
import { homeFor, useAuth } from '../auth/AuthContext';

/** Chỉ nhớ tên đăng nhập (không bao giờ nhớ mật khẩu) để lần sau nhân viên chỉ cần gõ mật khẩu. */
const LAST_USER = 'rms.lastUser';

function readLastUser() {
  try {
    return localStorage.getItem(LAST_USER) ?? '';
  } catch {
    return '';
  }
}

export function LoginPage() {
  const { user, loading, login, mustChangePassword } = useAuth();
  const navigate = useNavigate();
  const [username, setUsername] = useState(readLastUser);
  const [password, setPassword] = useState('');
  const [show, setShow] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const passwordRef = useRef<HTMLInputElement>(null);
  const remembered = useRef(username !== '').current;

  if (loading) return <div className="center-screen">Đang tải…</div>;
  if (user) return <Navigate to={mustChangePassword ? '/change-password' : homeFor(user.roles)} replace />;

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const name = username.trim();
      const res = await login(name, password);
      try {
        localStorage.setItem(LAST_USER, name);
      } catch {
        /* trình duyệt chặn lưu trữ: lần sau gõ lại tên, không sao */
      }
      navigate(res.mustChangePassword ? '/change-password' : homeFor(res.roles), { replace: true });
    } catch (err) {
      setError(err);
      if (err instanceof ApiError && err.status === 401) {
        setPassword('');
        passwordRef.current?.focus();
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthLayout title="Đăng nhập" onSubmit={submit}
                foot="Quên mật khẩu? Nhờ quản lý đặt lại.">
      <div className="field">
        <label htmlFor="username">Tên đăng nhập</label>
        <input id="username" className="input" autoComplete="username" autoCapitalize="none" autoCorrect="off"
               spellCheck={false} enterKeyHint="next" required autoFocus={!remembered} value={username}
               onChange={(e) => setUsername(e.target.value)} />
      </div>
      <PasswordInput id="password" label="Mật khẩu" autoComplete="current-password" value={password} onChange={setPassword}
                     show={show} onToggle={() => setShow((s) => !s)}
                     inputRef={passwordRef} autoFocus={remembered} />
      <ErrorNotice error={error} />
      <button className="btn btn-primary btn-block btn-lg" disabled={busy}>
        {busy ? 'Đang đăng nhập…' : 'Đăng nhập'}
      </button>
    </AuthLayout>
  );
}
