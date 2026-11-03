import { NavLink, Outlet, useLocation } from 'react-router';

import type { Role } from '../api/types';
import { useAuth } from '../auth/AuthContext';

type Tab = { label: string; to: string; roles: Role[]; children?: { label: string; to: string; roles: Role[] }[] };

const M: Role[] = ['MANAGER'];
const MK: Role[] = ['MANAGER', 'KITCHEN'];

/** Các mục có trang con: trang con thành tab (bản thiết kế "Điều hướng: 26 mục → 8 mục"). URL cũ giữ nguyên. */
export const SECTIONS: Record<string, { title: string; tabs: Tab[] }> = {
  menu: {
    title: 'Thực đơn',
    tabs: [
      { label: 'Thực đơn ngày', to: '/menu/daily', roles: MK },
      { label: 'Món & công thức', to: '/menu/items', roles: M },
    ],
  },
  inventory: {
    title: 'Kho',
    tabs: [
      { label: 'Tồn kho', to: '/inventory/stock', roles: MK },
      {
        label: 'Phiếu kho',
        to: '/inventory/receipts',
        roles: MK,
        children: [
          { label: 'Nhập kho', to: '/inventory/receipts', roles: M },
          { label: 'Xuất cho bếp', to: '/inventory/issues', roles: MK },
          { label: 'Sơ chế', to: '/inventory/preparations', roles: MK },
          { label: 'Hoàn trả, hủy hàng', to: '/inventory/returns', roles: MK },
          { label: 'Kiểm kê', to: '/inventory/counts', roles: M },
        ],
      },
      { label: 'Nguyên liệu', to: '/inventory/ingredients', roles: MK },
      { label: 'Lịch sử', to: '/inventory/history', roles: M },
    ],
  },
  staff: {
    title: 'Nhân sự',
    tabs: [
      { label: 'Nhân viên', to: '/staff', roles: M },
      { label: 'Lịch ca', to: '/shifts', roles: M },
      { label: 'Chấm công', to: '/attendance', roles: M },
    ],
  },
  finance: {
    title: 'Tài chính',
    tabs: [
      { label: 'Báo cáo', to: '/finance/reports', roles: M },
      { label: 'Khoản chi', to: '/finance/expenses', roles: M },
      { label: 'Đối soát tiền mặt', to: '/finance/cash', roles: M },
    ],
  },
  settings: {
    title: 'Cài đặt',
    tabs: [{ label: 'Bàn', to: '/settings/tables', roles: M }],
  },
};

/** Khung một mục: tiêu đề + thanh tab (lọc theo vai trò) + trang con. */
export function SectionLayout({ section }: { section: keyof typeof SECTIONS }) {
  const { hasRole } = useAuth();
  const { pathname } = useLocation();
  const def = SECTIONS[section];
  const tabs = def.tabs
    .map((t) => ({ ...t, children: t.children?.filter((c) => hasRole(...c.roles)) }))
    .filter((t) => hasRole(...t.roles) && (!t.children || t.children.length > 0));
  const current = tabs.find((t) => t.to === pathname || t.children?.some((c) => c.to === pathname));

  return (
    <div className="section-wrap">
      <div className="section-head">
        <h1>{def.title}</h1>
        {tabs.length > 1 && (
          <nav className="tabbar" aria-label={`Mục ${def.title}`}>
            {tabs.map((t) => (
              <NavLink key={t.label} to={t.children ? t.children[0].to : t.to}
                       aria-current={t === current ? 'page' : undefined} className={t === current ? 'active' : undefined}>
                {t.label}
              </NavLink>
            ))}
          </nav>
        )}
      </div>
      {current?.children && current.children.length > 1 && (
        <nav className="subtabs" aria-label={`Loại ${current.label.toLowerCase()}`}>
          {current.children.map((c) => (
            <NavLink key={c.to} to={c.to} end>
              {c.label}
            </NavLink>
          ))}
        </nav>
      )}
      <Outlet />
    </div>
  );
}
