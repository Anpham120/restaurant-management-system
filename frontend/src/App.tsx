import { lazy, Suspense } from 'react'
import { Navigate, Route, Routes } from 'react-router'
import { Spin } from 'antd'
import { useAuth } from './auth/AuthContext'
import { RequireRole } from './auth/RequireRole'
import { homePath } from './utils/format'

// Each screen is its own chunk, so a guest's phone downloads only the guest page.
const StaffLayout = lazy(() => import('./layouts/StaffLayout'))
const LoginPage = lazy(() => import('./pages/LoginPage'))
const GuestPage = lazy(() => import('./pages/GuestPage'))
const TablesPage = lazy(() => import('./pages/TablesPage'))
const OrderPage = lazy(() => import('./pages/OrderPage'))
const KitchenPage = lazy(() => import('./pages/KitchenPage'))
const CashierPage = lazy(() => import('./pages/CashierPage'))
const MenuAdminPage = lazy(() => import('./pages/admin/MenuAdminPage'))
const TablesAdminPage = lazy(() => import('./pages/admin/TablesAdminPage'))
const InventoryPage = lazy(() => import('./pages/admin/InventoryPage'))
const ReportsPage = lazy(() => import('./pages/admin/ReportsPage'))
const EmployeesPage = lazy(() => import('./pages/admin/EmployeesPage'))
const SettingsPage = lazy(() => import('./pages/admin/SettingsPage'))
const SchedulePage = lazy(() => import('./pages/admin/SchedulePage'))
const MePage = lazy(() => import('./pages/MePage'))

function Home() {
  const { user, loading } = useAuth()
  if (loading) return <Spin fullscreen />
  return <Navigate to={user ? homePath(user.role) : '/login'} replace />
}

export default function App() {
  return (
    <Suspense fallback={<Spin fullscreen />}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/q/:token" element={<GuestPage />} />
        <Route element={<StaffLayout />}>
          <Route path="/tables" element={<RequireRole roles={['WAITER']}><TablesPage /></RequireRole>} />
          <Route path="/orders/:id" element={<RequireRole roles={['WAITER']}><OrderPage /></RequireRole>} />
          <Route path="/kitchen" element={<RequireRole roles={['CHEF']}><KitchenPage /></RequireRole>} />
          <Route path="/cashier" element={<RequireRole roles={['CASHIER']}><CashierPage /></RequireRole>} />
          <Route path="/admin/menu" element={<RequireRole roles={['MANAGER']}><MenuAdminPage /></RequireRole>} />
          <Route path="/admin/tables" element={<RequireRole roles={['MANAGER']}><TablesAdminPage /></RequireRole>} />
          <Route path="/admin/inventory" element={<RequireRole roles={['MANAGER']}><InventoryPage /></RequireRole>} />
          <Route path="/admin/reports" element={<RequireRole roles={['MANAGER']}><ReportsPage /></RequireRole>} />
          <Route path="/admin/schedule" element={<RequireRole roles={['MANAGER']}><SchedulePage /></RequireRole>} />
          <Route path="/admin/employees" element={<RequireRole roles={['ADMIN']}><EmployeesPage /></RequireRole>} />
          <Route path="/admin/settings" element={<RequireRole roles={['ADMIN']}><SettingsPage /></RequireRole>} />
          <Route path="/me" element={<MePage />} />
        </Route>
        <Route path="*" element={<Home />} />
      </Routes>
    </Suspense>
  )
}
