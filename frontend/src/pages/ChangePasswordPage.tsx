import { useState, type FormEvent } from 'react';
import { Navigate, useNavigate } from 'react-router';

import { api } from '../api/client';
import { AuthLayout, PasswordInput } from '../components/AuthLayout';
import { ErrorNotice } from '../components/ErrorNotice';
import { useToast } from '../components/Toast';
import { homeFor, useAuth } from '../auth/AuthContext';

export function ChangePasswordPage() {
  const { user, loading, mustChangePassword, passwordChanged, logout } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const [show, setShow] = useState(false);

  if (loading) return <div className="center-screen">Đang tải…</div>;
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
    <AuthLayout title="Đổi mật khẩu" onSubmit={submit}
                lead={mustChangePassword ? 'Bạn đang dùng mật khẩu tạm. Đặt mật khẩu riêng để tiếp tục.' : undefined}
                foot={
                  <button type="button" className="btn btn-quiet" onClick={mustChangePassword ? logout : () => navigate(-1)}>
                    {mustChangePassword ? 'Đăng xuất' : 'Quay lại'}
                  </button>
                }>
      <PasswordInput id="cur" label="Mật khẩu hiện tại" autoComplete="current-password" value={current} onChange={setCurrent}
                     show={show} onToggle={() => setShow((v) => !v)} autoFocus />
      <PasswordInput id="new" label="Mật khẩu mới, ít nhất 8 ký tự" autoComplete="new-password" value={next} onChange={setNext}
                     show={show} onToggle={() => setShow((v) => !v)} minLength={8} maxLength={72} />
      <PasswordInput id="again" label="Nhập lại mật khẩu mới" autoComplete="new-password" value={confirm} onChange={setConfirm}
                     show={show} onToggle={() => setShow((v) => !v)} aria-invalid={mismatch} />
      {mismatch && <div className="notice notice-warn">Hai lần nhập mật khẩu mới chưa khớp.</div>}
      <ErrorNotice error={error} />
      <button className="btn btn-primary btn-block btn-lg" disabled={busy || mismatch || next.length < 8}>
        {busy ? 'Đang lưu…' : 'Đổi mật khẩu'}
      </button>
    </AuthLayout>
  );
}
