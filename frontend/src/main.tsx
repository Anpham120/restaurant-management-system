import { StrictMode, type ReactNode } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router';

import { ApiError } from './api/client';
import type { Role } from './api/types';
import { AuthProvider, homeFor, useAuth } from './auth/AuthContext';
import { Shell } from './components/Shell';
import { ToastProvider } from './components/Toast';
import { ChangePasswordPage } from './pages/ChangePasswordPage';
import { InvoicePage } from './pages/InvoicePage';
import { KitchenPage } from './pages/KitchenPage';
import { LoginPage } from './pages/LoginPage';
import { OrderPage } from './pages/OrderPage';
import { TablesPage } from './pages/TablesPage';
import { CashPage } from './pages/finance/CashPage';
import { ExpensesPage } from './pages/finance/ExpensesPage';
import { ReportsPage } from './pages/finance/ReportsPage';
import { CountsPage } from './pages/inventory/CountsPage';
import { HistoryPage } from './pages/inventory/HistoryPage';
import { IngredientsPage } from './pages/inventory/IngredientsPage';
import { IssuesPage } from './pages/inventory/IssuesPage';
import { PreparationsPage } from './pages/inventory/PreparationsPage';
import { ReceiptsPage } from './pages/inventory/ReceiptsPage';
import { ReturnsPage } from './pages/inventory/ReturnsPage';
import { StockPage } from './pages/inventory/StockPage';
import { SchedulePage } from './pages/me/SchedulePage';
import { DailyMenuPage } from './pages/menu/DailyMenuPage';
import { MenuItemsPage } from './pages/menu/MenuItemsPage';
import { TablesAdminPage } from './pages/settings/TablesAdminPage';
import { AttendancePage } from './pages/staff/AttendancePage';
import { EmployeesPage } from './pages/staff/EmployeesPage';
import { ShiftsPage } from './pages/staff/ShiftsPage';
import './styles.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // lỗi nghiệp vụ / quyền không thử lại; lỗi mạng thử lại 2 lần
      retry: (count, e) => !(e instanceof ApiError && e.status >= 400 && e.status < 500) && count < 2,
      refetchOnWindowFocus: true,
    },
    mutations: { retry: false },
  },
});

function RequireAuth({ roles, children }: { roles?: Role[]; children: ReactNode }) {
  const { user, loading, mustChangePassword, hasRole } = useAuth();
  if (loading) return <div className="center-screen">Đang tải…</div>;
  if (!user) return <Navigate to="/login" replace />;
  if (mustChangePassword) return <Navigate to="/change-password" replace />;
  if (roles && !hasRole(...roles)) return <Navigate to={homeFor(user.roles)} replace />;
  return <>{children}</>;
}

const M: Role[] = ['MANAGER'];
const MK: Role[] = ['MANAGER', 'KITCHEN'];

function guarded(path: string, roles: Role[], page: ReactNode) {
  return <Route key={path} path={path} element={<RequireAuth roles={roles}>{page}</RequireAuth>} />;
}

function Home() {
  const { user, loading } = useAuth();
  if (loading) return <div className="center-screen">Đang tải…</div>;
  return <Navigate to={user ? homeFor(user.roles) : '/login'} replace />;
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <AuthProvider>
          <BrowserRouter>
            <Routes>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/change-password" element={<ChangePasswordPage />} />
              <Route
                element={
                  <RequireAuth>
                    <Shell />
                  </RequireAuth>
                }
              >
                <Route path="/tables" element={<RequireAuth roles={['WAITER', 'CASHIER', 'MANAGER']}><TablesPage /></RequireAuth>} />
                <Route path="/orders/:orderId" element={<RequireAuth roles={['WAITER', 'CASHIER', 'MANAGER']}><OrderPage /></RequireAuth>} />
                <Route path="/orders/:orderId/invoice" element={<RequireAuth roles={['CASHIER', 'MANAGER']}><InvoicePage /></RequireAuth>} />
                <Route path="/kitchen" element={<RequireAuth roles={['KITCHEN', 'MANAGER']}><KitchenPage /></RequireAuth>} />
                {guarded('/menu/daily', MK, <DailyMenuPage />)}
                {guarded('/menu/items', M, <MenuItemsPage />)}
                {guarded('/inventory/stock', MK, <StockPage />)}
                {guarded('/inventory/receipts', M, <ReceiptsPage />)}
                {guarded('/inventory/issues', MK, <IssuesPage />)}
                {guarded('/inventory/preparations', MK, <PreparationsPage />)}
                {guarded('/inventory/returns', MK, <ReturnsPage />)}
                {guarded('/inventory/counts', M, <CountsPage />)}
                {guarded('/inventory/ingredients', MK, <IngredientsPage />)}
                {guarded('/inventory/history', M, <HistoryPage />)}
                {guarded('/staff', M, <EmployeesPage />)}
                {guarded('/shifts', M, <ShiftsPage />)}
                {guarded('/attendance', M, <AttendancePage />)}
                {guarded('/finance/expenses', M, <ExpensesPage />)}
                {guarded('/finance/cash', M, <CashPage />)}
                {guarded('/finance/reports', M, <ReportsPage />)}
                {guarded('/settings/tables', M, <TablesAdminPage />)}
                <Route path="/me/schedule" element={<SchedulePage />} />
              </Route>
              <Route path="*" element={<Home />} />
            </Routes>
          </BrowserRouter>
        </AuthProvider>
      </ToastProvider>
    </QueryClientProvider>
  </StrictMode>,
);
