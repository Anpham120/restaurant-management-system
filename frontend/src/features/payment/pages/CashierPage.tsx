import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App, Button, Card, Col, Empty, Flex, InputNumber, Modal, Popconfirm, Result, Row, Table, Tabs, Tag, Typography } from 'antd'
import { PercentageOutlined, PrinterOutlined, SplitCellsOutlined, UserAddOutlined } from '@ant-design/icons'
import { api, errorMessage } from '@/shared/api/client'
import type { BankTransaction, Order, Payment, PaymentInstruction, Settings, WebhookStatus } from '@/shared/api/types'
import { useAuth } from '@/features/auth/context/AuthContext'
import AttachCustomerModal from '@/features/customer/components/AttachCustomerModal'
import BuyerModal from '@/features/einvoice/components/BuyerModal'
import StatusTag from '@/features/order/components/StatusTag'
import AdjustmentModal from '../components/AdjustmentModal'
import BillSlip from '../components/BillSlip'
import CashShiftBar from '../components/CashShiftBar'
import SplitPaymentModal from '../components/SplitPaymentModal'
import TransferQr from '../components/TransferQr'
import { useCashShift } from '../hooks/useCashShift'
import { adjustmentLabel, adjustmentReason, adjustmentStatusColor, adjustmentStatusLabel, isOpen } from '../utils/adjustment'
import { orderTitle } from '../utils/bill'
import { usePrintSlip } from '@/shared/print/usePrintSlip'
import { cashSuggestions, channelLabel, hasRole, money, time } from '@/shared/utils/format'

/** FR-08: bills, cash, VietQR with automatic confirmation, manual confirmation, unmatched transfers, webhook warning. FR-17: the drawer shift. */
export default function CashierPage() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [instruction, setInstruction] = useState<PaymentInstruction | null>(null)
  const [cashOpen, setCashOpen] = useState(false)
  const [received, setReceived] = useState<number | null>(null)
  const [adjusting, setAdjusting] = useState(false)
  // FR-08.12: the part of a split bill being taken; null takes the whole rest.
  const [part, setPart] = useState<number | null>(null)
  const [splitting, setSplitting] = useState(false)
  const [attaching, setAttaching] = useState(false)
  const [buyerOpen, setBuyerOpen] = useState(false)
  // What was paid and left when the VietQR code was made, to tell a part that came in from a bill that changed.
  const [askedWhen, setAskedWhen] = useState<{ paid: number; due: number } | null>(null)
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
  const cashShift = useCashShift()
  const printer = usePrintSlip()

  const onError = (e: unknown) => message.error(errorMessage(e))
  /** FR-08.9: a bill while the order is open, the receipt once it is paid. */
  const printSlip = async (o: Order) => {
    try {
      const payments = o.status === 'PAID' ? (await api.get<Payment[]>(`/orders/${o.id}/payments`)).data : undefined
      printer.print(<BillSlip order={o} settings={settings.data} payments={payments} />)
    } catch (e) {
      onError(e)
    }
  }
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['orders'] })
    queryClient.invalidateQueries({ queryKey: ['order', selectedId] })
  }

  const payCash = useMutation({
    mutationFn: ({ received, amount }: { received: number; amount: number | null }) =>
      api
        .post<Payment>(`/orders/${selectedId}/payments/cash`, { receivedAmount: received, amount: amount ?? undefined })
        .then((r) => r.data),
    onSuccess: (payment) => {
      setCashOpen(false)
      setPart(null)
      message.success(`Đã thu ${money(payment.amount)}. Tiền thối: ${money(payment.change ?? 0)}`)
      refresh()
      queryClient.invalidateQueries({ queryKey: ['cash-shift'] })
    },
    onError,
  })
  const requestTransfer = useMutation({
    mutationFn: (amount: number | undefined) =>
      api.post<PaymentInstruction>(`/orders/${selectedId}/payments/transfer`, amount ? { amount } : undefined).then((r) => r.data),
    onSuccess: (qr) => {
      setInstruction(qr)
      setAskedWhen(order.data ? { paid: order.data.paidAmount, due: order.data.due } : null)
    },
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
  // BR-43: a part paid by transfer leaves the rest to take.
  const partPaid = !!(instruction && o && askedWhen && o.paidAmount > askedWhen.paid)
  const qr = instruction && o && askedWhen && o.paidAmount === askedWhen.paid && o.due === askedWhen.due ? instruction : null
  const cashAmount = part ?? o?.due ?? 0
  // BR-47: the app takes the money of an app order, not the counter.
  const blocked = !o || o.pendingCount > 0 || o.pendingAdjustmentCount > 0 || o.channel !== null

  const bill = !o ? (
    <Empty description="Chọn một đơn để tính tiền" />
  ) : o.status === 'PAID' ? (
    <Result
      status="success"
      title={`${orderTitle(o)}: đã nhận đủ ${money(o.total)}`}
      extra={[
        // FR-20.3: a company asks for its own e-invoice.
        <Button key="buyer" onClick={() => setBuyerOpen(true)}>
          Hoá đơn công ty
        </Button>,
        <Button key="done" onClick={() => setSelectedId(null)}>
          Xong
        </Button>,
      ]}
    />
  ) : (
    <Flex vertical gap={12}>
      {/* FR-19.2: the guest by phone number, for their visits and what they agreed to hear. */}
      <Flex justify="space-between" align="center" gap={8}>
        <Typography.Text type={o.customerId ? undefined : 'secondary'}>
          {o.customerId ? `Khách: ${o.customerName ?? 'Chưa có tên'}, ${o.customerPhone}` : 'Chưa gắn khách'}
        </Typography.Text>
        <Button size="small" icon={<UserAddOutlined />} onClick={() => setAttaching(true)}>
          {o.customerId ? 'Khách hàng' : 'Gắn khách'}
        </Button>
      </Flex>
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
      {(o.discountTotal > 0 || o.depositCredit > 0) && (
        <>
          <Flex justify="space-between">
            <span>Tiền món</span>
            <span>{money(o.subtotal)}</span>
          </Flex>
          {o.discountTotal > 0 && (
            <Flex justify="space-between">
              <span>Giảm</span>
              <span>-{money(o.discountTotal)}</span>
            </Flex>
          )}
          {/* BR-42: the deposit of the booking, already paid by transfer. */}
          {o.depositCredit > 0 && (
            <Flex justify="space-between">
              <span>Cọc đã trả</span>
              <span>-{money(o.depositCredit)}</span>
            </Flex>
          )}
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
      {/* FR-08.13: a split bill shows what was taken and what is left. */}
      {o.paidAmount > 0 && (
        <>
          <Flex justify="space-between">
            <span>Đã thu</span>
            <span>-{money(o.paidAmount)}</span>
          </Flex>
          <Flex justify="space-between">
            <Typography.Text strong>Còn phải thu</Typography.Text>
            <Typography.Text strong>{money(o.due)}</Typography.Text>
          </Flex>
        </>
      )}
      {o.channel && (
        <Alert type="info" showIcon title={`Đơn ${channelLabel[o.channel]}: app thu tiền. Phục vụ bấm Giao shipper ở trang đơn khi giao hàng.`} />
      )}
      {o.pendingCount > 0 && <Alert type="error" showIcon title="Còn món khách gửi qua QR chưa xác nhận. Nhờ phục vụ xử lý trước." />}
      {o.pendingAdjustmentCount > 0 && (
        <Alert type="warning" showIcon title="Có khoản giảm vượt hạn mức đang chờ quản lý duyệt, chưa thanh toán được." />
      )}
      {o.pendingCount === 0 && o.unservedCount > 0 && (
        <Alert type="warning" showIcon title={`Còn ${o.unservedCount} món chưa ra, kiểm tra với khách trước khi thu`} />
      )}
      {partPaid && <Alert type="success" showIcon title="Đã nhận chuyển khoản phần vừa rồi" />}
      {instruction && !qr && !partPaid && (
        <Alert type="info" showIcon title="Tổng tiền đã đổi, mã chuyển khoản cũ không dùng được nữa. Tạo lại mã." />
      )}
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
          <Flex gap={8} wrap>
            <Button icon={<PercentageOutlined />} disabled={o.subtotal <= 0 || o.paidAmount > 0} onClick={() => setAdjusting(true)}>
              Giảm giá, tặng món
            </Button>
            <Button icon={<SplitCellsOutlined />} disabled={blocked || o.due <= 0} onClick={() => setSplitting(true)}>
              Tách bill
            </Button>
          </Flex>
          <Flex gap={8}>
            <Button
              size="large"
              style={{ flex: 1 }}
              disabled={blocked || !cashShift.data}
              title={cashShift.data ? undefined : 'Chưa mở ca'}
              onClick={() => {
                setPart(null)
                setReceived(o.due)
                setCashOpen(true)
              }}
            >
              Tiền mặt
            </Button>
            <Button
              size="large"
              type="primary"
              style={{ flex: 1 }}
              disabled={blocked || o.due <= 0}
              loading={requestTransfer.isPending}
              onClick={() => requestTransfer.mutate(undefined)}
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
      <CashShiftBar />
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
        okButtonProps={{ disabled: !o || received === null || received < cashAmount }}
        confirmLoading={payCash.isPending}
        onCancel={() => {
          setCashOpen(false)
          setPart(null)
        }}
        onOk={() => received !== null && payCash.mutate({ received, amount: part })}
        destroyOnHidden
      >
        {o && (
          <Flex vertical gap={12}>
            <Typography.Text>
              {part === null ? 'Cần thu' : 'Cần thu lần này'}: <Typography.Text strong>{money(cashAmount)}</Typography.Text>
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
              {cashSuggestions(cashAmount).map((v) => (
                <Button key={v} onClick={() => setReceived(v)}>
                  {money(v)}
                </Button>
              ))}
            </Flex>
            {received !== null && received >= cashAmount && (
              <Typography.Title level={4} style={{ margin: 0 }}>
                Tiền thối: {money(received - cashAmount)}
              </Typography.Title>
            )}
          </Flex>
        )}
      </Modal>
      {splitting && o && (
        <SplitPaymentModal
          order={o}
          onClose={() => setSplitting(false)}
          onCash={(amount) => {
            setSplitting(false)
            setPart(amount)
            setReceived(amount)
            setCashOpen(true)
          }}
          onTransfer={(amount) => {
            setSplitting(false)
            requestTransfer.mutate(amount)
          }}
        />
      )}
      {attaching && o && <AttachCustomerModal order={o} onClose={() => setAttaching(false)} />}
      {buyerOpen && o && <BuyerModal orderId={o.id} onClose={() => setBuyerOpen(false)} />}
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
