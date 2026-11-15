import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';

import { api, setAuthProblemHandler, tokenStore } from '../api/client';
import type { LoginResponse, MeResponse, Role } from '../api/types';

interface AuthState {
  user: MeResponse | null;
  /** Đang kiểm token lưu từ lần trước. */
  loading: boolean;
  mustChangePassword: boolean;
  login: (username: string, password: string) => Promise<LoginResponse>;
  logout: () => void;
  /** Đổi mật khẩu thu hồi token cũ: lưu token mới server trả về. */
  passwordChanged: (res: LoginResponse) => void;
  hasRole: (...roles: Role[]) => boolean;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [user, setUser] = useState<MeResponse | null>(null);
  const [loading, setLoading] = useState(() => tokenStore.get() !== null);
  const [mustChangePassword, setMustChangePassword] = useState(false);

  /** Chỉ xóa phiên trên trình duyệt (token đã hết hiệu lực hoặc bị thu hồi). */
  const clearSession = useCallback(() => {
    tokenStore.set(null);
    setUser(null);
    setMustChangePassword(false);
    queryClient.clear();
  }, [queryClient]);

  /** Người dùng bấm đăng xuất: thu hồi token phía server (mọi thiết bị), rồi xóa phiên. Lỗi mạng vẫn đăng xuất. */
  const logout = useCallback(() => {
    if (tokenStore.get()) api.post('/api/auth/logout').catch(() => {});
    clearSession();
  }, [clearSession]);

  useEffect(() => {
    setAuthProblemHandler((e) => {
      if (e.code === 'PASSWORD_CHANGE_REQUIRED') setMustChangePassword(true);
      else clearSession();
    });
  }, [clearSession]);

  // Mở lại trang: token còn thì lấy lại thông tin người dùng (vai trò hiện hành do backend đọc từ DB).
  useEffect(() => {
    if (!tokenStore.get()) return;
    api
      .get<MeResponse>('/api/auth/me')
      .then(setUser)
      .catch(() => tokenStore.set(null))
      .finally(() => setLoading(false));
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const res = await api.post<LoginResponse>('/api/auth/login', { username, password });
    tokenStore.set(res.accessToken);
    setMustChangePassword(res.mustChangePassword);
    setUser(await api.get<MeResponse>('/api/auth/me'));
    return res;
  }, []);

  const value = useMemo<AuthState>(
    () => ({
      user,
      loading,
      mustChangePassword,
      login,
      logout,
      passwordChanged: (res) => {
        tokenStore.set(res.accessToken);
        setMustChangePassword(false);
      },
      hasRole: (...roles) => !!user && roles.some((r) => user.roles.includes(r)),
    }),
    [user, loading, mustChangePassword, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth phải nằm trong AuthProvider');
  return ctx;
}

/** Trang đầu tiên theo vai trò. */
export function homeFor(roles: Role[]): string {
  if (roles.includes('MANAGER')) return '/dashboard';
  if (roles.includes('WAITER') || roles.includes('CASHIER')) return '/tables';
  if (roles.includes('KITCHEN')) return '/kitchen';
  return '/login';
}
