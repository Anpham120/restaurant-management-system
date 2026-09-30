import { useCallback, useState, type ReactNode } from 'react'
import { Navigate, Outlet, useLocation, useNavigate } from 'react-router'
import { useQueryClient } from '@tanstack/react-query'
import { App, Badge, Button, Form, Input, Layout, Menu, Modal, Spin, Typography } from 'antd'
import {
  AccountBookOutlined,
  BarChartOutlined,
  BookOutlined,
  CalendarOutlined,
  CoffeeOutlined,
  DollarOutlined,
  FieldTimeOutlined,
  FireOutlined,
  InboxOutlined,
  KeyOutlined,
  LogoutOutlined,
  QrcodeOutlined,
  SettingOutlined,
  TableOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { api, errorMessage } from '../api/client'
import type { RealtimeMessage, Role } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import ServiceRequestsButton from '../components/ServiceRequestsButton'
import SoundButton from '../components/SoundButton'
import { useRealtime } from '../realtime/useRealtime'
import { hasRole, roleLabel } from '../utils/format'
import { pageAlerts, ring, setSoundWanted, soundWanted } from '../utils/sound'

/** role null: every signed-in employee. */
const NAV: { key: string; label: string; icon: ReactNode; role: Role | null }[] = [
  { key: '/tables', label: 'Sơ đồ bàn', icon: <TableOutlined />, role: 'WAITER' },
  { key: '/kitchen', label: 'Bếp', icon: <FireOutlined />, role: 'CHEF' },
  { key: '/cashier', label: 'Thu ngân', icon: <DollarOutlined />, role: 'CASHIER' },
  { key: '/admin/menu', label: 'Thực đơn', icon: <BookOutlined />, role: 'MANAGER' },
  { key: '/admin/tables', label: 'Bàn và QR', icon: <QrcodeOutlined />, role: 'MANAGER' },
  { key: '/admin/inventory', label: 'Kho', icon: <InboxOutlined />, role: 'MANAGER' },
  { key: '/admin/reports', label: 'Báo cáo', icon: <BarChartOutlined />, role: 'MANAGER' },
  { key: '/admin/schedule', label: 'Xếp ca', icon: <CalendarOutlined />, role: 'MANAGER' },
  { key: '/admin/attendance', label: 'Chấm công', icon: <FieldTimeOutlined />, role: 'MANAGER' },
  { key: '/admin/leave', label: 'Nghỉ phép', icon: <CoffeeOutlined />, role: 'MANAGER' },
  { key: '/admin/employees', label: 'Nhân viên', icon: <TeamOutlined />, role: 'ADMIN' },
  { key: '/admin/payroll', label: 'Bảng lương', icon: <AccountBookOutlined />, role: 'ADMIN' },
  { key: '/admin/settings', label: 'Cài đặt', icon: <SettingOutlined />, role: 'ADMIN' },
  { key: '/me', label: 'Của tôi', icon: <UserOutlined />, role: null },
]

export default function StaffLayout() {
  const { user, token, loading, logout } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  const [passwordOpen, setPasswordOpen] = useState(false)
  const [soundOn, setSoundOn] = useState(soundWanted)

  // One subscription for every staff screen: refetch whatever the notice touches (FR-07.3, NFR-01), and ring when
  // this page cares about the alert (FR-07.5, FR-06.6).
  const onMessage = useCallback(
    (m: RealtimeMessage) => {
      const refresh = (...keys: unknown[][]) => keys.forEach((queryKey) => queryClient.invalidateQueries({ queryKey }))
      switch (m.type) {
        case 'ORDER_CHANGED':
        case 'PAYMENT_PAID':
          refresh(['tables'], ['orders'], ['kitchen'], ['order', m.orderId])
          break
        case 'MENU_CHANGED':
          refresh(['menu-items'], ['categories'])
          break
        case 'TABLES_CHANGED':
          // A deleted table takes its calls with it.
          refresh(['tables'], ['service-requests'])
          break
        case 'BANK_TRANSACTION':
          refresh(['bank-transactions'])
          break
        case 'REQUESTS_CHANGED':
          refresh(['service-requests'])
          break
      }
      if (m.alert && soundOn && pageAlerts(location.pathname).includes(m.alert)) ring(m.alert)
    },
    [queryClient, soundOn, location.pathname],
  )
  const connected = useRealtime(user ? ['/topic/staff'] : [], onMessage, token)

  if (loading) return <Spin fullscreen />
  if (!user) return <Navigate to="/login" replace />

  const items = NAV.filter((n) => n.role === null || hasRole(user.role, n.role)).map(({ key, label, icon }) => ({ key, label, icon }))
  const selected = NAV.find((n) => location.pathname.startsWith(n.key))?.key ?? (location.pathname.startsWith('/orders') ? '/tables' : '')

  const changePassword = async (values: { currentPassword: string; newPassword: string }) => {
    try {
      await api.post('/auth/change-password', values)
      message.success('Đã đổi mật khẩu')
      setPasswordOpen(false)
    } catch (e) {
      message.error(errorMessage(e))
    }
  }

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Layout.Sider breakpoint="lg" collapsedWidth={0} theme="light">
        <div style={{ padding: 16, fontWeight: 700, color: '#b45309' }}>Bếp Nhà &amp; Nướng</div>
        <Menu mode="inline" items={items} selectedKeys={[selected]} onClick={({ key }) => navigate(key)} />
      </Layout.Sider>
      <Layout>
        <Layout.Header
          style={{ background: '#fff', padding: '0 16px', display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: 8 }}
        >
          {hasRole(user.role, 'WAITER') && <ServiceRequestsButton />}
          {pageAlerts(location.pathname).length > 0 && (
            <SoundButton
              on={soundOn}
              onChange={(on) => {
                setSoundWanted(on)
                setSoundOn(on)
              }}
            />
          )}
          <Badge
            status={connected ? 'success' : 'warning'}
            text={<span className="wide-only">{connected ? 'Trực tuyến' : 'Đang kết nối lại'}</span>}
            title={connected ? 'Trực tuyến' : 'Đang kết nối lại'}
          />
          <Typography.Text strong style={{ marginLeft: 8 }}>
            {user.fullName}
          </Typography.Text>
          <Typography.Text type="secondary" className="wide-only">
            ({roleLabel[user.role]})
          </Typography.Text>
          <Button icon={<KeyOutlined />} onClick={() => setPasswordOpen(true)} title="Đổi mật khẩu" />
          <Button icon={<LogoutOutlined />} onClick={logout} title="Đăng xuất" />
        </Layout.Header>
        <Layout.Content style={{ padding: 16 }}>
          <Outlet />
        </Layout.Content>
      </Layout>
      <Modal title="Đổi mật khẩu" open={passwordOpen} onCancel={() => setPasswordOpen(false)} footer={null} destroyOnHidden>
        <Form layout="vertical" onFinish={changePassword}>
          <Form.Item name="currentPassword" label="Mật khẩu hiện tại" rules={[{ required: true }]}>
            <Input.Password />
          </Form.Item>
          <Form.Item name="newPassword" label="Mật khẩu mới" rules={[{ required: true, min: 6, message: 'Ít nhất 6 ký tự' }]}>
            <Input.Password />
          </Form.Item>
          <Button type="primary" htmlType="submit" block>
            Lưu
          </Button>
        </Form>
      </Modal>
    </Layout>
  )
}
