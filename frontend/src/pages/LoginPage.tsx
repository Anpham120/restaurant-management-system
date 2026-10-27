import { useState, type FormEvent } from 'react';
import { Navigate, useNavigate } from 'react-router';

import { ErrorNotice } from '../components/ErrorNotice';
import { homeFor, useAuth } from '../auth/AuthContext';

export function LoginPage() {
  const { user, login, mustChangePassword } = useAuth();
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to={mustChangePassword ? '/change-password' : homeFor(user.roles)} replace />;

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const res = await login(username.trim(), password);
      navigate(res.mustChangePassword ? '/change-password' : homeFor(res.roles), { replace: true });
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-page">
      <section className="auth-side">
        <div className="big">Mở ca, vào bàn.</div>
        <p>Gọi món, theo dõi bếp và thu tiền trên cùng một màn hình. Đăng nhập bằng tài khoản quản lý cấp cho bạn.</p>
      </section>
      <form className="auth-form" onSubmit={submit}>
        <h1>Đăng nhập</h1>
        <div className="field">
          <label htmlFor="username">Tên đăng nhập</label>
          <input id="username" className="input" autoComplete="username" autoFocus required value={username}
                 onChange={(e) => setUsername(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="password">Mật khẩu</label>
          <input id="password" className="input" type="password" autoComplete="current-password" required value={password}
                 onChange={(e) => setPassword(e.target.value)} />
        </div>
        <ErrorNotice error={error} />
        <button className="btn btn-primary btn-block" disabled={busy}>
          {busy ? 'Đang đăng nhập…' : 'Đăng nhập'}
        </button>
      </form>
    </div>
  );
}
