import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router';

import type { Role } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { TableMark } from './AuthLayout';
import { roleLabel } from '../lib/format';

const M: Role[] = ['MANAGER'];
const MK: Role[] = ['MANAGER', 'KITCHEN'];
const ALL: Role[] = ['MANAGER', 'WAITER', 'KITCHEN', 'CASHIER'];

export const NAV: { group: string; items: { to: string; label: string; roles: Role[] }[] }[] = [
  {
    group: 'Vận hành',
    items: [
      { to: '/tables', label: 'Sơ đồ bàn', roles: ['WAITER', 'CASHIER', 'MANAGER'] },
      { to: '/kitchen', label: 'Bếp', roles: MK },
    ],
  },
  {
    group: 'Thực đơn',
    items: [
      { to: '/menu/daily', label: 'Thực đơn ngày', roles: MK },
      { to: '/menu/items', label: 'Món và công thức', roles: M },
    ],
  },
  {
    group: 'Kho',
    items: [
      { to: '/inventory/stock', label: 'Tồn kho', roles: MK },
      { to: '/inventory/receipts', label: 'Nhập kho', roles: M },
      { to: '/inventory/issues', label: 'Xuất cho bếp', roles: MK },
      { to: '/inventory/preparations', label: 'Sơ chế', roles: MK },
      { to: '/inventory/returns', label: 'Hoàn trả, hủy hàng', roles: MK },
      { to: '/inventory/counts', label: 'Kiểm kê', roles: M },
      { to: '/inventory/ingredients', label: 'Nguyên liệu', roles: MK },
      { to: '/inventory/history', label: 'Lịch sử kho', roles: M },
    ],
  },
  {
    group: 'Nhân sự',
    items: [
      { to: '/staff', label: 'Nhân viên', roles: M },
      { to: '/shifts', label: 'Ca và phân ca', roles: M },
      { to: '/attendance', label: 'Chấm công', roles: M },
    ],
  },
  {
    group: 'Tài chính',
    items: [
      { to: '/finance/expenses', label: 'Khoản chi', roles: M },
      { to: '/finance/cash', label: 'Đối soát tiền mặt', roles: M },
      { to: '/finance/reports', label: 'Báo cáo', roles: M },
    ],
  },
  {
    group: 'Cài đặt',
    items: [{ to: '/settings/tables', label: 'Quản lý bàn', roles: M }],
  },
  {
    group: 'Cá nhân',
    items: [
      { to: '/me/schedule', label: 'Lịch làm của tôi', roles: ALL },
      { to: '/change-password', label: 'Đổi mật khẩu', roles: ALL },
    ],
  },
];

export function Shell() {
  const { user, logout, hasRole } = useAuth();
  const [open, setOpen] = useState(false);
  const location = useLocation();
  useEffect(() => setOpen(false), [location.pathname]);

  return (
    <div className="shell">
      <aside className="rail" data-open={open}>
        <div className="rail-top">
          <div className="wordmark">
            <TableMark className="wordmark-mark" />
            Atlas Restaurant
          </div>
          <button type="button" className="btn btn-sm menu-toggle" aria-expanded={open} onClick={() => setOpen((o) => !o)}>
            {open ? 'Đóng' : 'Menu'}
          </button>
        </div>
        <div className="rail-panel">
          <nav aria-label="Chức năng">
            {NAV.map((g) => {
              const items = g.items.filter((n) => hasRole(...n.roles));
              if (items.length === 0) return null;
              return (
                <div key={g.group} className="nav-group">
                  <div className="group">{g.group}</div>
                  {items.map((n) => (
                    <NavLink key={n.to} to={n.to} end={n.to === '/staff'}>
                      {n.label}
                    </NavLink>
                  ))}
                </div>
              );
            })}
          </nav>
          <div className="who">
            <span>
              <strong>{user?.fullName ?? user?.username}</strong>
              {user?.roles.map((r) => roleLabel[r]).join(', ')}
            </span>
            <button type="button" className="btn btn-sm" onClick={logout}>
              Đăng xuất
            </button>
          </div>
        </div>
      </aside>
      <Outlet />
    </div>
  );
}
