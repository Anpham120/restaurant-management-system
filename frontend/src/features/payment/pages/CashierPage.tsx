import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App, Button, Card, Col, Empty, Flex, InputNumber, Modal, Popconfirm, Result, Row, Table, Tabs, Tag, Typography } from 'antd'
import { PercentageOutlined, PrinterOutlined } from '@ant-design/icons'
import { api, errorMessage } from '@/shared/api/client'
import type { BankTransaction, Order, Payment, PaymentInstruction, Settings, WebhookStatus } from '@/shared/api/types'
import { useAuth } from '@/features/auth/context/AuthContext'
import StatusTag from '@/features/order/components/StatusTag'
import AdjustmentModal from '../components/AdjustmentModal'
import BillSlip from '../components/BillSlip'
import TransferQr from '../components/TransferQr'
import { adjustmentLabel, adjustmentReason, adjustmentStatusColor, adjustmentStatusLabel, isOpen } from '../utils/adjustment'
import { orderTitle } from '../utils/bill'
import { usePrintSlip } from '@/shared/print/usePrintSlip'
import { cashSuggestions, hasRole, money, time } from '@/shared/utils/format'

/** FR-08: bills, cash, VietQR with automatic confirmation, manual confirmation, unmatched transfers, webhook warning. */
export default function CashierPage() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [instruction, setInstruction] = useState<PaymentInstruction | null>(null)
  const [cashOpen, setCashOpen] = useState(false)
  const [received, setReceived] = useState<number | null>(null)
  const [adjusting, setAdjusting] = useState(false)
  const { user } = useAuth()

  const orders = useQuery({ queryKey: ['orders', 'OPEN'], queryFn: () => api.get<Order[]>('/orders').then((r) => r.data) })
  const order = useQuery({
    queryKey: ['order', selectedId],
    queryFn: () => api.get<Order>(`/orders/${selectedId}`).then((r) => r.data),
    enabled: selectedId !== null,
  })
  const unmatched = useQuery({
    queryKey: ['bank-transactions'],
    queryFn: () => api.get<BankTransaction[]>('/bank-transactions').then((r) => r.data),
  })
  const webhook = useQuery({
    queryKey: ['webhook-status'],
    queryFn: () => api.get<WebhookStatus>('/bank-transactions/webhook-status').then((r) => r.data),
  })
  const settings = useQuery({ queryKey: ['settings'], queryFn: () => api.get<Settings>('/settings').then((r) => r.data) })
  const printer = usePrintSlip()

  const onError = (e: unknown) => message.error(errorMessage(e))
  /** FR-08.9: a bill while the order is open, the receipt once it is paid. */
  const printSlip = async (o: Order) => {
    try {
      const payment = o.status === 'PAID' ? (await api.get<Payment>(`/orders/${o.id}/payment`)).data : undefined
      printer.print(<BillSlip order={o} settings={settings.data} payment={payment} />)
    } catch (e) {
      onError(e)
    }
  }
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['orders'] })
    queryClient.invalidateQueries({ queryKey: ['order', selectedId] })
  }

  const payCash = useMutation({
    mutationFn: (amount: number) =>
      api.post<Payment>(`/orders/${selectedId}/payments/cash`, { receivedAmount: amount }).then((r) => r.data),
    onSuccess: (payment) => {
      setCashOpen(false)
      message.success(`Đã thu tiền. Tiền thối: ${money(payment.change ?? 0)}`)
      refresh()
    },
    onError,
  })
  const requestTransfer = useMutation({
    mutationFn: () => api.post<PaymentInstruction>(`/orders/${selectedId}/payments/transfer`).then((r) => r.data),
    onSuccess: setInstruction,
    onError,
  })
  const confirmManually = useMutation({
    mutationFn: (paymentId: number) => api.post<Payment>(`/payments/${paymentId}/confirm`).then((r) => r.data),
    onSuccess: () => {
      message.success('Đã xác nhận chuyển khoản')
      refresh()
    },
    onError,
  })
  /** FR-08.10: the bill changed, so the order list and the selected bill show the new total. */
  const onAdjusted = (updated: Order) => {
    queryClient.setQueryData(['order', updated.id], updated)
    queryClient.invalidateQueries({ queryKey: ['orders'] })
  }
  const cancelAdjustment = useMutation({
    mutationFn: (id: number) => api.post<Order>(`/adjustments/${id}/cancel`).then((r) => r.data),
    onSuccess: onAdjusted,
    onError,
  })

  const select = (id: number) => {
    setSelectedId(id)
    setInstruction(null)
  }

  const o = order.data
  const billItems = o?.items.filter((i) => i.status !== 'CANCELLED') ?? []
  const adjustments = o?.adjustments.filter((a) => a.status !== 'CANCELLED') ?? []
  // BR-14: a discount changes the total and voids the code shown; the cashier asks for a new one.
  const qr = instruction && o && instruction.amount === o.total ? instruction : null
  const blocked = !o || o.pendingCount > 0 || o.pendingAdjustmentCount > 0

  const bill = !o ? (
    <Empty description="Chọn một đơn để tính tiền" />
  ) : o.status === 'PAID' ? (
    <Result
      status="success"
      title={`${orderTitle(o)}: đã nhận đủ ${money(o.total)}`}
      extra={<Button onClick={() => setSelectedId(null)}>Xong</Button>}
    />
  ) : (
    <Flex vertical gap={12}>
      <Table
        size="small"
        rowKey="id"
        pagination={false}
        dataSource={billItems}
        columns={[
          { title: 'Món', dataIndex: 'itemName' },
          { title: 'SL', dataIndex: 'quantity', width: 48 },
          { title: 'Đơn giá', render: (_, i) => money(i.unitPrice) },
          { title: 'Thành tiền', render: (_, i) => money(i.unitPrice * i.quantity) },
          { title: '', render: (_, i) => <StatusTag status={i.status} /> },
        ]}
      />
      {adjustments.length > 0 && (
        <Table
          size="small"
          rowKey="id"
          pagination={false}
          showHeader={false}
          dataSource={adjustments}
          columns={[
            {
              render: (_, a) => (
                <>
                  {adjustmentLabel(a)} <Typography.Text type="secondary">({adjustmentReason(a)})</Typography.Text>
                </>
              ),
            },
            { width: 110, render: (_, a) => `-${money(a.amount)}` },
            { width: 110, render: (_, a) => <Tag color={adjustmentStatusColor[a.status]}>{adjustmentStatusLabel[a.status]}</Tag> },
            {
              width: 60,
              render: (_, a) =>
                isOpen(a) && (
                  <Popconfirm title="Huỷ khoản giảm này?" onConfirm={() => cancelAdjustment.mutate(a.id)}>
                    <Button size="small" type="link" danger>
                      Huỷ
                    </Button>
                  </Popconfirm>
                ),
            },
          ]}
        />
      )}
      {o.discountTotal > 0 && (
        <>
          <Flex justify="space-between">
            <span>Tiền món</span>
            <span>{money(o.subtotal)}</span>
          </Flex>
          <Flex justify="space-between">
            <span>Giảm</span>
            <span>-{money(o.discountTotal)}</span>
          </Flex>
        </>
      )}
      <Flex justify="space-between">
        <Typography.Title level={4} style={{ margin: 0 }}>
          Tổng cộng
        </Typography.Title>
        <Typography.Title level={4} style={{ margin: 0 }}>
          {money(o.total)}
        </Typography.Title>
      </Flex>
      {o.pendingCount > 0 && <Alert type="error" showIcon title="Còn món khách gửi qua QR chưa xác nhận. Nhờ phục vụ xử lý trước." />}
      {o.pendingAdjustmentCount > 0 && (
        <Alert type="warning" showIcon title="Có khoản giảm vượt hạn mức đang chờ quản lý duyệt, chưa thanh toán được." />
      )}
      {o.pendingCount === 0 && o.unservedCount > 0 && (
        <Alert type="warning" showIcon title={`Còn ${o.unservedCount} món chưa ra, kiểm tra với khách trước khi thu`} />
      )}
      {instruction && !qr && <Alert type="info" showIcon title="Tổng tiền đã đổi, mã chuyển khoản cũ không dùng được nữa. Tạo lại mã." />}
      {qr ? (
        <Card size="small">
          <TransferQr instruction={qr} />
          <Flex justify="center" gap={8} style={{ marginTop: 12 }}>
            <Button onClick={() => setInstruction(null)}>Đổi cách trả</Button>
            <Popconfirm
              title="Đã thấy tiền về trong app ngân hàng?"
              description="Chỉ xác nhận tay khi đã kiểm tra đúng số tiền và nội dung."
              onConfirm={() => confirmManually.mutate(qr.paymentId)}
            >
              <Button>Xác nhận tay</Button>
            </Popconfirm>
          </Flex>
        </Card>
      ) : (
        <>
          <Button icon={<PercentageOutlined />} disabled={o.subtotal <= 0} onClick={() => setAdjusting(true)}>
            Giảm giá, tặng món
          </Button>
          <Flex gap={8}>
            <Button
              size="large"
              style={{ flex: 1 }}
              disabled={blocked}
              onClick={() => {
                setReceived(o.total)
                setCashOpen(true)
              }}
            >
              Tiền mặt
            </Button>
            <Button
              size="large"
              type="primary"
              style={{ flex: 1 }}
              disabled={blocked || o.total <= 0}
              loading={requestTransfer.isPending}
              onClick={() => requestTransfer.mutate()}
            >
              Chuyển khoản (VietQR)
            </Button>
          </Flex>
        </>
      )}
    </Flex>
  )

  return (
    <>
      <div className="page-title">
        <Typography.Title level={3}>Thu ngân</Typography.Title>
      </div>
      {/* FR-08.8: SePay keeps failing, so transfers are not confirmed by themselves. */}
      {webhook.data?.failing && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          title="Chuyển khoản đang không tự xác nhận"
          description={`Khách báo đã chuyển khoản thì kiểm tra tiền về trong app ngân hàng rồi bấm Xác nhận tay, và báo quản lý. Chi tiết: webhook SePay lỗi ${webhook.data.failures} lần liên tiếp từ ${time(webhook.data.since)}, lần gần nhất: ${webhook.data.lastError}.`}
        />
      )}
      <Tabs
        items={[
          {
            key: 'orders',
            label: 'Đơn đang mở',
            children: (
              <Row gutter={[16, 16]}>
                <Col xs={24} md={10}>
                  <Table<Order>
                    size="small"
                    rowKey="id"
                    pagination={false}
                    loading={orders.isLoading}
                    dataSource={orders.data ?? []}
                    onRow={(record) => ({ onClick: () => select(record.id), style: { cursor: 'pointer' } })}
                    rowClassName={(record) => (record.id === selectedId ? 'ant-table-row-selected' : '')}
                    columns={[
                      { title: 'Đơn', render: (_, r) => orderTitle(r) },
                      { title: 'Tổng', render: (_, r) => money(r.total) },
                      {
                        title: '',
                        render: (_, r) => (r.pendingCount > 0 ? <Tag color="gold">Chờ xác nhận</Tag> : null),
                      },
                    ]}
                  />
                </Col>
                <Col xs={24} md={14}>
                  <Card
                    title={o ? orderTitle(o) : 'Hoá đơn'}
                    size="small"
                    extra={
                      o &&
                      o.status !== 'CANCELLED' && (
                        <Button size="small" icon={<PrinterOutlined />} onClick={() => printSlip(o)}>
                          {o.status === 'PAID' ? 'In phiếu thanh toán' : 'In tạm tính'}
                        </Button>
                      )
                    }
                  >
                    {bill}
                  </Card>
                </Col>
              </Row>
            ),
          },
          {
            key: 'unmatched',
            label: `Giao dịch không khớp (${unmatched.data?.length ?? 0})`,
            children: (
              <Table<BankTransaction>
                size="small"
                rowKey="id"
                dataSource={unmatched.data ?? []}
                columns={[
                  { title: 'Thời gian', render: (_, t) => time(t.receivedAt) },
                  { title: 'Số tiền', render: (_, t) => money(t.amount) },
                  { title: 'Nội dung', dataIndex: 'content' },
                  { title: 'Lý do', dataIndex: 'note' },
                ]}
              />
            ),
          },
        ]}
      />

      <Modal
        title="Thu tiền mặt"
        open={cashOpen}
        okText="Xác nhận đã thu"
        okButtonProps={{ disabled: !o || received === null || received < o.total }}
        confirmLoading={payCash.isPending}
        onCancel={() => setCashOpen(false)}
        onOk={() => received !== null && payCash.mutate(received)}
        destroyOnHidden
      >
        {o && (
          <Flex vertical gap={12}>
            <Typography.Text>
              Tổng cần thu: <Typography.Text strong>{money(o.total)}</Typography.Text>
            </Typography.Text>
            <InputNumber<number>
              size="large"
              style={{ width: '100%' }}
              min={0}
              step={1000}
              value={received}
              onChange={setReceived}
              formatter={(v) => `${v ?? ''}`.replace(/\B(?=(\d{3})+(?!\d))/g, '.')}
              parser={(v) => Number((v ?? '').replace(/\./g, ''))}
              addonAfter="đ"
            />
            <Flex gap={8} wrap>
              {cashSuggestions(o.total).map((v) => (
                <Button key={v} onClick={() => setReceived(v)}>
                  {money(v)}
                </Button>
              ))}
            </Flex>
            {received !== null && received >= o.total && (
              <Typography.Title level={4} style={{ margin: 0 }}>
                Tiền thối: {money(received - o.total)}
              </Typography.Title>
            )}
          </Flex>
        )}
      </Modal>
      {adjusting && o && (
        <AdjustmentModal
          order={o}
          manager={hasRole(user?.role, 'MANAGER')}
          onClose={() => setAdjusting(false)}
          onSaved={onAdjusted}
        />
      )}
      {printer.area}
    </>
  )
}
