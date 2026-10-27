import { useState, type FormEvent } from 'react';
import { Navigate, useNavigate } from 'react-router';

import { api } from '../api/client';
import { ErrorNotice } from '../components/ErrorNotice';
import { useToast } from '../components/Toast';
import { homeFor, useAuth } from '../auth/AuthContext';

export function ChangePasswordPage() {
  const { user, mustChangePassword, passwordChanged, logout } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  if (!user) return <Navigate to="/login" replace />;

  const mismatch = confirm.length > 0 && next !== confirm;

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (next !== confirm) return;
    setBusy(true);
    setError(null);
    try {
      await api.post('/api/auth/change-password', { currentPassword: current, newPassword: next });
      passwordChanged();
      toast('Đã đổi mật khẩu');
      navigate(homeFor(user!.roles), { replace: true });
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-page">
      <section className="auth-side">
        <div className="big">Đặt mật khẩu của riêng bạn.</div>
        <p>
          {mustChangePassword
            ? 'Tài khoản đang dùng mật khẩu tạm do quản lý cấp. Đổi mật khẩu để bắt đầu làm việc.'
            : 'Mật khẩu mới có hiệu lực từ lần đăng nhập sau.'}
        </p>
      </section>
      <form className="auth-form" onSubmit={submit}>
        <h1>Đổi mật khẩu</h1>
        <div className="field">
          <label htmlFor="cur">Mật khẩu hiện tại</label>
          <input id="cur" className="input" type="password" autoComplete="current-password" required value={current}
                 onChange={(e) => setCurrent(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="new">Mật khẩu mới (ít nhất 8 ký tự)</label>
          <input id="new" className="input" type="password" autoComplete="new-password" minLength={8} maxLength={72} required
                 value={next} onChange={(e) => setNext(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="again">Nhập lại mật khẩu mới</label>
          <input id="again" className="input" type="password" autoComplete="new-password" required value={confirm}
                 aria-invalid={mismatch} onChange={(e) => setConfirm(e.target.value)} />
        </div>
        {mismatch && <div className="notice notice-warn">Hai lần nhập mật khẩu mới chưa khớp.</div>}
        <ErrorNotice error={error} />
        <button className="btn btn-primary btn-block" disabled={busy || mismatch || next.length < 8}>
          {busy ? 'Đang lưu…' : 'Đổi mật khẩu'}
        </button>
        {mustChangePassword ? (
          <button type="button" className="btn btn-quiet" onClick={logout}>
            Đăng xuất
          </button>
        ) : (
          <button type="button" className="btn btn-quiet" onClick={() => navigate(-1)}>
            Quay lại
          </button>
        )}
      </form>
    </div>
  );
}
