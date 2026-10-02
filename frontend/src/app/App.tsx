import { lazy, Suspense } from 'react'
import { Navigate, Route, Routes } from 'react-router'
import { Spin } from 'antd'
import { useAuth } from '@/features/auth/context/AuthContext'
import { RequireRole } from '@/features/auth/components/RequireRole'
import { homePath } from '@/shared/utils/format'

// Each screen is its own chunk, so a guest's phone downloads only the guest page.
const StaffLayout = lazy(() => import('./StaffLayout'))
const LoginPage = lazy(() => import('@/features/auth/pages/LoginPage'))
const GuestPage = lazy(() => import('@/features/order/pages/GuestPage'))
const TablesPage = lazy(() => import('@/features/table/pages/TablesPage'))
const ReservationsPage = lazy(() => import('@/features/reservation/pages/ReservationsPage'))
const OrderPage = lazy(() => import('@/features/order/pages/OrderPage'))
const KitchenPage = lazy(() => import('@/features/order/pages/KitchenPage'))
const CashierPage = lazy(() => import('@/features/payment/pages/CashierPage'))
const MenuAdminPage = lazy(() => import('@/features/menu/pages/MenuAdminPage'))
const TablesAdminPage = lazy(() => import('@/features/table/pages/TablesAdminPage'))
const InventoryPage = lazy(() => import('@/features/inventory/pages/InventoryPage'))
const CustomersPage = lazy(() => import('@/features/customer/pages/CustomersPage'))
const EInvoicesPage = lazy(() => import('@/features/einvoice/pages/EInvoicesPage'))
const ReportsPage = lazy(() => import('@/features/report/pages/ReportsPage'))
const AuditPage = lazy(() => import('@/features/audit/pages/AuditPage'))
const CashShiftsPage = lazy(() => import('@/features/payment/pages/CashShiftsPage'))
const EmployeesPage = lazy(() => import('@/features/employee/pages/EmployeesPage'))
const SettingsPage = lazy(() => import('@/features/settings/pages/SettingsPage'))
const SchedulePage = lazy(() => import('@/features/schedule/pages/SchedulePage'))
const AttendancePage = lazy(() => import('@/features/attendance/pages/AttendancePage'))
const LeavePage = lazy(() => import('@/features/leave/pages/LeavePage'))
const PayrollPage = lazy(() => import('@/features/payroll/pages/PayrollPage'))
const MePage = lazy(() => import('@/features/employee/pages/MePage'))

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
          <Route path="/reservations" element={<RequireRole roles={['WAITER']}><ReservationsPage /></RequireRole>} />
          <Route path="/orders/:id" element={<RequireRole roles={['WAITER']}><OrderPage /></RequireRole>} />
          <Route path="/kitchen" element={<RequireRole roles={['CHEF']}><KitchenPage /></RequireRole>} />
          <Route path="/cashier" element={<RequireRole roles={['CASHIER']}><CashierPage /></RequireRole>} />
          <Route path="/admin/menu" element={<RequireRole roles={['MANAGER']}><MenuAdminPage /></RequireRole>} />
          <Route path="/admin/tables" element={<RequireRole roles={['MANAGER']}><TablesAdminPage /></RequireRole>} />
          <Route path="/admin/inventory" element={<RequireRole roles={['MANAGER']}><InventoryPage /></RequireRole>} />
          <Route path="/admin/customers" element={<RequireRole roles={['MANAGER']}><CustomersPage /></RequireRole>} />
          <Route path="/admin/einvoices" element={<RequireRole roles={['MANAGER']}><EInvoicesPage /></RequireRole>} />
          <Route path="/admin/reports" element={<RequireRole roles={['MANAGER']}><ReportsPage /></RequireRole>} />
          <Route path="/admin/audit" element={<RequireRole roles={['MANAGER']}><AuditPage /></RequireRole>} />
          <Route path="/admin/cash-shifts" element={<RequireRole roles={['MANAGER']}><CashShiftsPage /></RequireRole>} />
          <Route path="/admin/schedule" element={<RequireRole roles={['MANAGER']}><SchedulePage /></RequireRole>} />
          <Route path="/admin/attendance" element={<RequireRole roles={['MANAGER']}><AttendancePage /></RequireRole>} />
          <Route path="/admin/leave" element={<RequireRole roles={['MANAGER']}><LeavePage /></RequireRole>} />
          <Route path="/admin/employees" element={<RequireRole roles={['ADMIN']}><EmployeesPage /></RequireRole>} />
          <Route path="/admin/payroll" element={<RequireRole roles={['ADMIN']}><PayrollPage /></RequireRole>} />
          <Route path="/admin/settings" element={<RequireRole roles={['ADMIN']}><SettingsPage /></RequireRole>} />
          <Route path="/me" element={<MePage />} />
        </Route>
        <Route path="*" element={<Home />} />
      </Routes>
    </Suspense>
  )
}
