import { useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App, Button, Card, Col, Flex, Input, Modal, Popconfirm, Result, Row, Spin, Table, Tag, Typography } from 'antd'
import { ArrowLeftOutlined, PrinterOutlined } from '@ant-design/icons'
import { api, errorMessage } from '@/shared/api/client'
import type { MenuItem, Order, OrderItem, Settings } from '@/shared/api/types'
import { useAuth } from '@/features/auth/context/AuthContext'
import BillSlip from '@/features/payment/components/BillSlip'
import { usePrintSlip } from '@/shared/print/usePrintSlip'
import CartPanel from '../components/CartPanel'
import MenuPicker, { toSections } from '../components/MenuPicker'
import StatusTag from '../components/StatusTag'
import { useCart } from '../hooks/useCart'
import { hasRole, money, orderStatusLabel } from '@/shared/utils/format'

/** FR-05, FR-06.3: take an order, confirm guest dishes, serve and cancel dishes. */
export default function OrderPage() {
  const orderId = Number(useParams().id)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { user } = useAuth()
  const { message } = App.useApp()
  const cart = useCart()
  const [cancelling, setCancelling] = useState<OrderItem | null>(null)
  const [reason, setReason] = useState('')

  const order = useQuery({
    queryKey: ['order', orderId],
    queryFn: () => api.get<Order>(`/orders/${orderId}`).then((r) => r.data),
  })
  const menu = useQuery({ queryKey: ['menu-items'], queryFn: () => api.get<MenuItem[]>('/menu-items').then((r) => r.data) })
  const settings = useQuery({ queryKey: ['settings'], queryFn: () => api.get<Settings>('/settings').then((r) => r.data) })
  const printer = usePrintSlip()

  const onOrder = (updated: Order) => queryClient.setQueryData(['order', orderId], updated)
  const onError = (e: unknown) => message.error(errorMessage(e))

  const send = useMutation({
    mutationFn: () => api.post<Order>(`/orders/${orderId}/items`, { items: cart.toItemLines() }).then((r) => r.data),
    onSuccess: (updated) => {
      onOrder(updated)
      cart.clear()
      message.success('Đã gửi bếp')
    },
    onError,
  })
  const confirmPending = useMutation({
    mutationFn: () => api.post<Order>(`/orders/${orderId}/confirm-pending`).then((r) => r.data),
    onSuccess: (updated) => {
      onOrder(updated)
      message.success('Đã xác nhận, món đã vào bếp')
    },
    onError,
  })
  const serve = useMutation({
    mutationFn: (itemId: number) => api.patch(`/order-items/${itemId}/status`, { status: 'SERVED' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['order', orderId] }),
    onError,
  })
  const cancelItem = useMutation({
    mutationFn: ({ itemId, why }: { itemId: number; why: string }) => api.post(`/order-items/${itemId}/cancel`, { reason: why }),
    onSuccess: () => {
      setCancelling(null)
      queryClient.invalidateQueries({ queryKey: ['order', orderId] })
    },
    onError,
  })
  const cancelOrder = useMutation({
    mutationFn: () => api.post<Order>(`/orders/${orderId}/cancel`).then((r) => r.data),
    onSuccess: () => navigate('/tables'),
    onError,
  })

  if (order.isLoading) return <Spin />
  if (!order.data) return <Result status="404" title="Không tìm thấy đơn" />
  const o = order.data
  const title = o.type === 'TAKEAWAY' ? `Mang về #${o.id}` : `Bàn ${o.tableName}`
  const isManager = hasRole(user?.role, 'MANAGER')

  if (o.status !== 'OPEN') {
    return (
      <Result
        status={o.status === 'PAID' ? 'success' : 'info'}
        title={`${title}: ${orderStatusLabel[o.status]}`}
        extra={<Button onClick={() => navigate('/tables')}>Về sơ đồ bàn</Button>}
      />
    )
  }

  const canCancel = (item: OrderItem) =>
    item.status === 'PENDING' || item.status === 'WAITING' || (isManager && (item.status === 'COOKING' || item.status === 'READY'))
  const needsReason = (item: OrderItem) => item.status !== 'WAITING'
  const allCancelled = o.items.every((i) => i.status === 'CANCELLED')

  return (
    <>
      <div className="page-title">
        <Flex align="center" gap={8}>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/tables')} aria-label="Quay lại" />
          <Typography.Title level={3}>{title}</Typography.Title>
          {o.guestCount && <Tag>{o.guestCount} khách</Tag>}
        </Flex>
        <Flex align="center" gap={8}>
          <Typography.Title level={4} style={{ margin: 0 }}>
            {money(o.total)}
          </Typography.Title>
          {/* FR-08.9: the bill guests check before paying. */}
          <Button icon={<PrinterOutlined />} onClick={() => printer.print(<BillSlip order={o} settings={settings.data} />)}>
            In tạm tính
          </Button>
        </Flex>
      </div>

      {o.pendingCount > 0 && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          title={`Khách gửi ${o.pendingCount} món qua QR, cần kiểm tra trước khi vào bếp`}
          action={
            <Button type="primary" loading={confirmPending.isPending} onClick={() => confirmPending.mutate()}>
              Xác nhận tất cả
            </Button>
          }
        />
      )}

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={14}>
          <Card title="Thực đơn" size="small">
            <MenuPicker sections={toSections(menu.data ?? [])} cart={cart} />
          </Card>
        </Col>
        <Col xs={24} lg={10}>
          <Card title="Món chờ gửi" size="small" style={{ marginBottom: 16 }}>
            <CartPanel cart={cart} sendLabel="Gửi bếp" sending={send.isPending} onSend={() => send.mutate()} />
          </Card>
          <Card
            title="Đã gọi"
            size="small"
            extra={
              allCancelled && (
                <Popconfirm title="Huỷ đơn và trả bàn?" onConfirm={() => cancelOrder.mutate()}>
                  <Button danger size="small">
                    Huỷ đơn
                  </Button>
                </Popconfirm>
              )
            }
          >
            <Table<OrderItem>
              size="small"
              rowKey="id"
              pagination={false}
              dataSource={o.items}
              columns={[
                {
                  title: 'Món',
                  render: (_, item) => (
                    <>
                      <Typography.Text delete={item.status === 'CANCELLED'}>{item.itemName}</Typography.Text>
                      {item.source === 'GUEST' && <Tag style={{ marginLeft: 4 }}>QR</Tag>}
                      {item.note && <div className="note">{item.note}</div>}
                      {item.cancelReason && <Typography.Text type="secondary">Lý do: {item.cancelReason}</Typography.Text>}
                    </>
                  ),
                },
                { title: 'SL', dataIndex: 'quantity', width: 48 },
                { title: 'Trạng thái', render: (_, item) => <StatusTag status={item.status} /> },
                {
                  title: '',
                  render: (_, item) => (
                    <Flex gap={4} wrap>
                      {item.status === 'READY' && (
                        <Button size="small" type="primary" onClick={() => serve.mutate(item.id)}>
                          Đã ra
                        </Button>
                      )}
                      {canCancel(item) && (
                        <Button
                          size="small"
                          danger
                          onClick={() => {
                            setReason('')
                            setCancelling(item)
                          }}
                        >
                          {item.status === 'PENDING' ? 'Từ chối' : 'Huỷ'}
                        </Button>
                      )}
                    </Flex>
                  ),
                },
              ]}
            />
          </Card>
        </Col>
      </Row>

      <Modal
        title={cancelling?.status === 'PENDING' ? `Từ chối món ${cancelling?.itemName}` : `Huỷ món ${cancelling?.itemName}`}
        open={cancelling !== null}
        okText="Xác nhận"
        okButtonProps={{ danger: true, disabled: cancelling !== null && needsReason(cancelling) && !reason.trim() }}
        confirmLoading={cancelItem.isPending}
        onCancel={() => setCancelling(null)}
        onOk={() => cancelling && cancelItem.mutate({ itemId: cancelling.id, why: reason })}
        destroyOnHidden
      >
        <Input.TextArea
          rows={3}
          maxLength={300}
          placeholder={cancelling && needsReason(cancelling) ? 'Lý do (bắt buộc, khách sẽ thấy nếu là món QR)' : 'Lý do (không bắt buộc)'}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
        />
      </Modal>
      {printer.area}
    </>
  )
}
