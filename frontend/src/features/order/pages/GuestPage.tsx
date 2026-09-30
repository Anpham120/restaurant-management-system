import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { useParams } from 'react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App, Button, Empty, Flex, Modal, Result, Spin, Tabs, Typography } from 'antd'
import { BellOutlined, FileTextOutlined } from '@ant-design/icons'
import { api, errorMessage } from '@/shared/api/client'
import type { GuestTable, MenuSection, PaymentInstruction, RealtimeMessage, ServiceRequestType } from '@/shared/api/types'
import CartPanel from '../components/CartPanel'
import MenuPicker from '../components/MenuPicker'
import StatusTag from '../components/StatusTag'
import TransferQr from '@/features/payment/components/TransferQr'
import { useCart } from '../hooks/useCart'
import { useRealtime } from '@/shared/realtime/useRealtime'
import { money } from '@/shared/utils/format'

/** FR-06.6: the call buttons, and what they say while the call waits for a waiter. */
const CALL_BUTTONS: { type: ServiceRequestType; label: string; waitingLabel: string; icon: ReactNode }[] = [
  { type: 'CALL_STAFF', label: 'Gọi nhân viên', waitingLabel: 'Đang chờ nhân viên', icon: <BellOutlined /> },
  { type: 'BILL', label: 'Yêu cầu tính tiền', waitingLabel: 'Đang chờ tính tiền', icon: <FileTextOutlined /> },
]

/** Guest page behind the table QR code (FR-06, FR-08.5). No login, phone-first. */
export default function GuestPage() {
  const token = useParams().token ?? ''
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  const cart = useCart()
  const [tab, setTab] = useState('menu')
  const [cartOpen, setCartOpen] = useState(false)
  const [instruction, setInstruction] = useState<PaymentInstruction | null>(null)
  const [paid, setPaid] = useState(false)

  const table = useQuery({
    queryKey: ['guest-table', token],
    queryFn: () => api.get<GuestTable>(`/public/tables/${token}`).then((r) => r.data),
    retry: false,
  })
  const menu = useQuery({
    queryKey: ['guest-menu'],
    queryFn: () => api.get<MenuSection[]>('/public/menu').then((r) => r.data),
  })

  const onMessage = useCallback(
    (m: RealtimeMessage) => {
      if (m.type === 'PAYMENT_PAID') {
        setPaid(true)
        setInstruction(null)
      }
      if (m.type === 'MENU_CHANGED') queryClient.invalidateQueries({ queryKey: ['guest-menu'] })
      queryClient.invalidateQueries({ queryKey: ['guest-table', token] })
    },
    [queryClient, token],
  )
  useRealtime(token ? [`/topic/guest/${token}`, '/topic/menu'] : [], onMessage)

  const send = useMutation({
    mutationFn: () => api.post<GuestTable>(`/public/tables/${token}/items`, { items: cart.toItemLines() }).then((r) => r.data),
    onSuccess: (data) => {
      queryClient.setQueryData(['guest-table', token], data)
      cart.clear()
      setCartOpen(false)
      setTab('ordered')
      message.success('Đã gửi món, nhân viên sẽ xác nhận ngay')
    },
    onError: (e) => message.error(errorMessage(e)),
  })
  const pay = useMutation({
    mutationFn: () => api.post<PaymentInstruction>(`/public/tables/${token}/payment`).then((r) => r.data),
    onSuccess: setInstruction,
    onError: (e) => message.error(errorMessage(e)),
  })
  const call = useMutation({
    mutationFn: (type: ServiceRequestType) =>
      api.post<GuestTable>(`/public/tables/${token}/requests`, { type }).then((r) => r.data),
    onSuccess: (data, type) => {
      queryClient.setQueryData(['guest-table', token], data)
      message.success(type === 'BILL' ? 'Đã báo nhân viên mang hoá đơn tới' : 'Đã gọi nhân viên')
    },
    onError: (e) => message.error(errorMessage(e)),
  })

  // FR-06.7: a call that is no longer waiting has been taken by a waiter.
  const openRequests = table.data?.openRequests
  const previousRequests = useRef<ServiceRequestType[]>(undefined)
  useEffect(() => {
    if (openRequests && previousRequests.current?.some((t) => !openRequests.includes(t))) {
      message.success('Nhân viên đang tới')
    }
    previousRequests.current = openRequests
  }, [openRequests, message])

  if (table.isLoading) return <Spin fullscreen />
  if (table.isError || !table.data) {
    return (
      <div className="center-page">
        <Result status="warning" title="Mã QR không còn hiệu lực" subTitle={errorMessage(table.error)} />
      </div>
    )
  }

  const { tableName, restaurantName, order } = table.data

  if (paid) {
    return (
      <div className="center-page">
        <Result
          status="success"
          title="Đã thanh toán"
          subTitle={`Cảm ơn quý khách đã dùng bữa tại ${restaurantName}!`}
          extra={<Button onClick={() => setPaid(false)}>Về thực đơn</Button>}
        />
      </div>
    )
  }

  const ordered = order ? (
    <Flex vertical gap={8} style={{ paddingTop: 8 }}>
      {order.items.map((item) => (
        <div key={item.id} className="menu-row">
          <div>
            <Typography.Text strong delete={item.status === 'CANCELLED'}>
              {item.itemName} × {item.quantity}
            </Typography.Text>
            {item.note && <div className="note">{item.note}</div>}
            {item.cancelReason && <Typography.Text type="danger">Lý do: {item.cancelReason}</Typography.Text>}
          </div>
          <StatusTag status={item.status} />
        </div>
      ))}
      <Flex justify="space-between">
        <Typography.Text>Tạm tính</Typography.Text>
        <Typography.Title level={4} style={{ margin: 0 }}>
          {money(order.total)}
        </Typography.Title>
      </Flex>
      {order.pendingCount > 0 && (
        <Alert type="info" showIcon title="Món mới đang chờ nhân viên xác nhận. Thanh toán được sau khi xác nhận." />
      )}
      <Button type="primary" size="large" block disabled={!order.canPay} loading={pay.isPending} onClick={() => pay.mutate()}>
        Thanh toán chuyển khoản
      </Button>
    </Flex>
  ) : (
    <Empty description="Bàn chưa gọi món" style={{ paddingTop: 24 }} />
  )

  return (
    <div className="guest-shell">
      <div className="guest-header">
        <div style={{ fontSize: 13, opacity: 0.85 }}>{restaurantName}</div>
        <div style={{ fontSize: 20, fontWeight: 700 }}>Bàn {tableName}</div>
      </div>
      <div className="guest-body">
        <Flex gap={8}>
          {CALL_BUTTONS.map(({ type, label, waitingLabel, icon }) => {
            const waiting = table.data.openRequests.includes(type)
            return (
              <Button
                key={type}
                icon={icon}
                style={{ flex: 1 }}
                disabled={waiting || (type === 'BILL' && !order)}
                loading={call.isPending && call.variables === type}
                onClick={() => call.mutate(type)}
              >
                {waiting ? waitingLabel : label}
              </Button>
            )
          })}
        </Flex>
        <Tabs
          activeKey={tab}
          onChange={setTab}
          items={[
            {
              key: 'menu',
              label: 'Thực đơn',
              children: menu.isLoading ? <Spin /> : <MenuPicker sections={menu.data ?? []} cart={cart} />,
            },
            { key: 'ordered', label: `Món đã gọi (${order?.items.length ?? 0})`, children: ordered },
          ]}
        />
      </div>

      {tab === 'menu' && cart.count > 0 && (
        <div className="guest-bar">
          <Button type="primary" size="large" block onClick={() => setCartOpen(true)}>
            Xem {cart.count} món đã chọn · {money(cart.total)}
          </Button>
        </div>
      )}

      <Modal title="Món đã chọn" open={cartOpen} onCancel={() => setCartOpen(false)} footer={null} destroyOnHidden>
        <CartPanel cart={cart} sendLabel="Gửi món" sending={send.isPending} onSend={() => send.mutate()} />
      </Modal>

      <Modal title="Chuyển khoản" open={instruction !== null} onCancel={() => setInstruction(null)} footer={null} destroyOnHidden>
        {instruction && <TransferQr instruction={instruction} />}
      </Modal>
    </div>
  )
}
