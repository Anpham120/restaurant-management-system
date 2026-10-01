import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Alert, App, Button, Card, Col, Flex, Form, Input, InputNumber, Modal, Row, Statistic, Table, Typography } from 'antd'
import { api, errorMessage } from '@/shared/api/client'
import type { CashExpense, CashShift } from '@/shared/api/types'
import { useAuth } from '@/features/auth/context/AuthContext'
import { hasRole, money, moneyInputProps, time } from '@/shared/utils/format'
import { useCashShift } from '../hooks/useCashShift'
import { differenceText, EXPENSE_LIMIT, OPENING_FLOAT } from '../utils/cashShift'

type Dialog = 'open' | 'expense' | 'close' | null

/** Posts to the shift API, says what happened and reloads the shift. */
function useShiftAction(path: string, done: (s: CashShift) => string, then: () => void) {
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  return useMutation({
    mutationFn: (body: object) => api.post<CashShift>(path, body).then((r) => r.data),
    onSuccess: (s) => {
      message.success(done(s))
      then()
      queryClient.invalidateQueries({ queryKey: ['cash-shift'] })
    },
    onError: (e) => message.error(errorMessage(e)),
  })
}

/** FR-17.1 → FR-17.3: the drawer shift on the cashier screen. Cash can only be taken while one is open (BR-39). */
export default function CashShiftBar() {
  const { user } = useAuth()
  const [dialog, setDialog] = useState<Dialog>(null)
  const shift = useCashShift()
  const finish = () => setDialog(null)
  const open = useShiftAction('/cash-shifts', (s) => `Đã mở ca #${s.id}`, finish)
  const expense = useShiftAction('/cash-shifts/current/expenses', () => 'Đã ghi phiếu chi', finish)
  const close = useShiftAction('/cash-shifts/current/close', (s) => `Đã chốt ca #${s.id}: ${differenceText(s.difference ?? 0)}`, finish)

  if (shift.isLoading) return null
  const s = shift.data

  return (
    <>
      {s ? (
        <Card size="small" style={{ marginBottom: 16 }}>
          <Flex justify="space-between" align="center" gap={8} wrap style={{ marginBottom: 8 }}>
            <Typography.Text>
              <Typography.Text strong>Ca #{s.id}</Typography.Text> · mở {time(s.openedAt)} · {s.openedByName}
            </Typography.Text>
            <Flex gap={8}>
              <Button onClick={() => setDialog('expense')}>Phiếu chi</Button>
              <Button type="primary" onClick={() => setDialog('close')}>Chốt ca</Button>
            </Flex>
          </Flex>
          <Row gutter={[16, 8]}>
            <Col xs={12} md={6}><Statistic title="Quỹ đầu ca" value={money(s.openingFloat)} /></Col>
            <Col xs={12} md={6}><Statistic title={`Thu tiền mặt (${s.cashPayments})`} value={money(s.cashTaken)} /></Col>
            <Col xs={12} md={6}><Statistic title="Phiếu chi" value={money(s.expenseTotal)} /></Col>
            <Col xs={12} md={6}><Statistic title="Tiền mặt dự kiến" value={money(s.expectedCash)} /></Col>
          </Row>
        </Card>
      ) : (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          title="Chưa mở ca"
          description="Mở ca với quỹ đầu ca trước khi thu tiền mặt."
          action={<Button type="primary" onClick={() => setDialog('open')}>Mở ca</Button>}
        />
      )}

      <Modal title="Mở ca" open={dialog === 'open'} onCancel={() => setDialog(null)} footer={null} destroyOnHidden>
        <Form layout="vertical" initialValues={{ openingFloat: OPENING_FLOAT }} onFinish={(v) => open.mutate(v)}>
          <Form.Item name="openingFloat" label="Quỹ đầu ca (tiền mặt có sẵn trong két)" rules={[{ required: true }]}>
            <InputNumber min={0} max={1_000_000_000} step={50_000} suffix="đ" style={{ width: '100%' }} {...moneyInputProps} />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={open.isPending}>Mở ca</Button>
        </Form>
      </Modal>

      <Modal title="Phiếu chi" open={dialog === 'expense'} onCancel={() => setDialog(null)} footer={null} destroyOnHidden>
        <Form layout="vertical" onFinish={(v) => expense.mutate(v)}>
          <Form.Item
            name="amount"
            label="Số tiền lấy từ két"
            rules={[{ required: true }]}
            extra={hasRole(user?.role, 'MANAGER') ? undefined : `Trên ${money(EXPENSE_LIMIT)} cần quản lý lập.`}
          >
            <InputNumber min={1} max={1_000_000_000} step={10_000} suffix="đ" style={{ width: '100%' }} {...moneyInputProps} />
          </Form.Item>
          <Form.Item name="reason" label="Lý do" rules={[{ required: true, whitespace: true, max: 300 }]}>
            <Input placeholder="Mua đá, trả tiền rau..." />
          </Form.Item>
          <Typography.Paragraph type="secondary">Phiếu chi đã ghi không sửa, không xoá được.</Typography.Paragraph>
          <Button type="primary" htmlType="submit" block loading={expense.isPending}>Ghi phiếu chi</Button>
        </Form>
      </Modal>

      <Modal title={s ? `Chốt ca #${s.id}` : ''} open={dialog === 'close'} onCancel={() => setDialog(null)} footer={null} destroyOnHidden>
        {s && <CloseForm shift={s} pending={close.isPending} onClose={(v) => close.mutate(v)} />}
      </Modal>
    </>
  )
}

/** BR-39: the count beside what the drawer should hold; a difference needs a reason. */
function CloseForm({ shift, pending, onClose }: { shift: CashShift; pending: boolean; onClose: (v: object) => void }) {
  const [form] = Form.useForm<{ countedCash?: number | null; note?: string }>()
  const counted = Form.useWatch('countedCash', form)
  const difference = typeof counted === 'number' ? counted - shift.expectedCash : null

  return (
    <Form form={form} layout="vertical" onFinish={(v) => onClose(v)}>
      <Table<CashExpense>
        size="small"
        rowKey="id"
        pagination={false}
        dataSource={shift.expenses}
        locale={{ emptyText: 'Không có phiếu chi' }}
        style={{ marginBottom: 16 }}
        columns={[
          { title: 'Phiếu chi', dataIndex: 'reason' },
          { title: 'Người chi', dataIndex: 'createdByName' },
          { title: 'Số tiền', render: (_, e) => money(e.amount) },
        ]}
      />
      <Typography.Paragraph>
        {money(shift.openingFloat)} quỹ đầu ca + {money(shift.cashTaken)} tiền mặt thu − {money(shift.expenseTotal)} phiếu chi ={' '}
        <Typography.Text strong>{money(shift.expectedCash)} dự kiến</Typography.Text>
      </Typography.Paragraph>
      <Form.Item name="countedCash" label="Tiền mặt đếm được trong két" rules={[{ required: true, message: 'Nhập số tiền đếm được' }]}>
        <InputNumber min={0} max={10_000_000_000} step={10_000} suffix="đ" style={{ width: '100%' }} {...moneyInputProps} />
      </Form.Item>
      {difference !== null && (
        <Typography.Paragraph strong type={difference === 0 ? 'success' : 'danger'}>
          {differenceText(difference)}
        </Typography.Paragraph>
      )}
      <Form.Item
        name="note"
        label="Lý do chênh lệch"
        rules={[{ required: difference !== null && difference !== 0, whitespace: true, message: 'Tiền đếm lệch dự kiến: cần ghi lý do' }, { max: 300 }]}
      >
        <Input.TextArea rows={2} />
      </Form.Item>
      <Typography.Paragraph type="secondary">Ca đã chốt không sửa được. Thu tiền mặt tiếp thì mở ca mới.</Typography.Paragraph>
      <Button type="primary" htmlType="submit" block loading={pending}>Chốt ca</Button>
    </Form>
  )
}
