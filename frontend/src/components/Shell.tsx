import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router';
import { useQuery } from '@tanstack/react-query';

import { api } from '../api/client';
import type { OrderItem, Role, TableResponse } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { roleLabel } from '../lib/format';
import { TableMark } from './AuthLayout';
import { Icon, type IconName } from './Icon';

const M: Role[] = ['MANAGER'];
const MK: Role[] = ['MANAGER', 'KITCHEN'];

/** 8 mục chính (bản thiết kế: 26 mục → 8, trang con thành tab). Mỗi vai trò chỉ thấy mục của mình. */
export const NAV: { to: string; label: string; icon: IconName; roles: Role[]; match: string[]; badge?: 'tables' | 'kitchen' }[] = [
  { to: '/dashboard', label: 'Tổng quan', icon: 'home', roles: M, match: ['/dashboard'] },
  { to: '/tables', label: 'Bàn & gọi món', icon: 'tables', roles: ['WAITER', 'CASHIER', 'MANAGER'], match: ['/tables', '/orders'], badge: 'tables' },
  { to: '/kitchen', label: 'Bếp', icon: 'kitchen', roles: MK, match: ['/kitchen'], badge: 'kitchen' },
  { to: '/menu/daily', label: 'Thực đơn', icon: 'menu', roles: MK, match: ['/menu'] },
  { to: '/inventory/stock', label: 'Kho', icon: 'stock', roles: MK, match: ['/inventory'] },
  { to: '/staff', label: 'Nhân sự', icon: 'staff', roles: M, match: ['/staff', '/shifts', '/attendance'] },
  { to: '/finance/reports', label: 'Tài chính', icon: 'finance', roles: M, match: ['/finance'] },
  { to: '/settings/tables', label: 'Cài đặt', icon: 'settings', roles: M, match: ['/settings'] },
];

function initials(name: string): string {
  const parts = name.trim().split(/\s+/);
  return ((parts.length > 1 ? parts[parts.length - 2][0] : '') + parts[parts.length - 1][0]).toUpperCase();
}

export function Shell() {
  const { user, logout, hasRole } = useAuth();
  const [open, setOpen] = useState(false);
  const location = useLocation();
  useEffect(() => setOpen(false), [location.pathname]);

  // Số trên menu: bàn đang có khách, món bếp chưa xong. Dùng chung cache với trang tương ứng.
  const seesTables = hasRole('WAITER', 'CASHIER', 'MANAGER');
  const seesKitchen = hasRole('KITCHEN', 'MANAGER');
  const tables = useQuery({
    queryKey: ['tables'],
    queryFn: () => api.get<TableResponse[]>('/api/tables'),
    enabled: seesTables,
    refetchInterval: 15_000,
  });
  const queue = useQuery({
    queryKey: ['kitchen-queue'],
    queryFn: () => api.get<OrderItem[]>('/api/kitchen/queue'),
    enabled: seesKitchen,
    refetchInterval: 15_000,
  });
  const badges = {
    tables: (tables.data ?? []).filter((t) => t.currentSessionId != null).length,
    kitchen: (queue.data ?? []).filter((i) => i.status !== 'READY').length,
  };

  const name = user?.fullName ?? user?.username ?? '';
  const isActive = (match: string[]) => match.some((m) => location.pathname === m || location.pathname.startsWith(m + '/'));

  return (
    <div className="shell">
      <aside className="rail" data-open={open}>
        <div className="rail-top">
          <div className="wordmark">
            <TableMark className="wordmark-mark" />
            <span>
              <b>Atlas</b>
              <small>Nhà hàng</small>
            </span>
          </div>
          <button type="button" className="btn btn-sm menu-toggle" aria-expanded={open} onClick={() => setOpen((o) => !o)}>
            {open ? 'Đóng' : 'Menu'}
          </button>
        </div>
        <div className="rail-panel">
          <nav aria-label="Chức năng">
            {NAV.filter((n) => hasRole(...n.roles)).map((n) => {
              const count = n.badge ? badges[n.badge] : 0;
              return (
                <NavLink key={n.to} to={n.to} className={isActive(n.match) ? 'active' : undefined}
                         aria-current={isActive(n.match) ? 'page' : undefined}>
                  <Icon name={n.icon} />
                  <span>{n.label}</span>
                  {count > 0 && <span className="badge num" aria-label={`${count} đang chờ`}>{count}</span>}
                </NavLink>
              );
            })}
          </nav>
          <div className="me">
            <NavLink to="/me/schedule">
              <Icon name="calendar" />
              Lịch làm của tôi
            </NavLink>
            <NavLink to="/change-password">
              <Icon name="key" />
              Đổi mật khẩu
            </NavLink>
            <div className="who">
              <span className="avatar" aria-hidden="true">{name ? initials(name) : '?'}</span>
              <span className="who-text">
                <strong>{name}</strong>
                {user?.roles.map((r) => roleLabel[r]).join(', ')}
              </span>
              <button type="button" className="btn btn-sm btn-quiet" onClick={logout} aria-label="Đăng xuất">
                <Icon name="logout" size={18} />
              </button>
            </div>
          </div>
        </div>
      </aside>
      <Outlet />
    </div>
  );
}
