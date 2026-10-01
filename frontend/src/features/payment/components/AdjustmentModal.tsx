import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Alert, App, Button, Flex, Input, InputNumber, Modal, Segmented, Select, Typography } from 'antd'
import { api, errorMessage } from '@/shared/api/client'
import type { AdjustmentReason, AdjustmentType, Order } from '@/shared/api/types'
import { money } from '@/shared/utils/format'
import { adjustmentOutcome, adjustmentReasonLabel, isOpen } from '../utils/adjustment'

interface Props {
  order: Order
  /** A manager is not held by the limit (BR-35). */
  manager: boolean
  onClose: () => void
  onSaved: (order: Order) => void
}

/** FR-08.10: a discount on the whole bill, or a dish line given free, with a reason. Render it only while open. */
export default function AdjustmentModal({ order, manager, onClose, onSaved }: Props) {
  const { message } = App.useApp()
  const [type, setType] = useState<AdjustmentType>('DISCOUNT')
  const [amount, setAmount] = useState<number | null>(null)
  const [itemId, setItemId] = useState<number>()
  const [reason, setReason] = useState<AdjustmentReason>()
  const [note, setNote] = useState('')

  const given = new Set(order.adjustments.filter(isOpen).map((a) => a.orderItemId))
  const dishes = order.items.filter((i) => i.status !== 'PENDING' && i.status !== 'CANCELLED' && !given.has(i.id))
  const dish = dishes.find((i) => i.id === itemId)
  const value = type === 'COMP' ? (dish ? dish.unitPrice * dish.quantity : 0) : (amount ?? 0)
  const outcome = value > 0 ? adjustmentOutcome(order, value, manager) : null
  const ready = value > 0 && reason !== undefined && (reason !== 'OTHER' || note.trim() !== '') && outcome !== 'over'

  const save = useMutation({
    mutationFn: () =>
      api
        .post<Order>(`/orders/${order.id}/adjustments`, {
          type,
          orderItemId: type === 'COMP' ? itemId : null,
          amount: type === 'DISCOUNT' ? amount : null,
          reason,
          note: note.trim() || null,
        })
        .then((r) => r.data),
    onSuccess: (updated) => {
      const waiting = updated.adjustments.at(-1)?.status === 'PENDING'
      if (waiting) message.warning('Vượt hạn mức, đã gửi quản lý duyệt')
      else message.success(type === 'COMP' ? 'Đã tặng món' : 'Đã giảm giá')
      onSaved(updated)
      onClose()
    },
    onError: (e) => message.error(errorMessage(e)),
  })

  const percent = (p: number) => Math.floor((order.subtotal * p) / 100 / 1000) * 1000

  return (
    <Modal
      open
      title="Giảm giá, tặng món"
      okText={outcome === 'approval' ? 'Gửi quản lý duyệt' : 'Áp dụng'}
      okButtonProps={{ disabled: !ready }}
      confirmLoading={save.isPending}
      onOk={() => save.mutate()}
      onCancel={onClose}
    >
      <Flex vertical gap={12}>
        <Segmented<AdjustmentType>
          block
          value={type}
          onChange={setType}
          options={[
            { value: 'DISCOUNT', label: 'Giảm tiền cả bill' },
            { value: 'COMP', label: 'Tặng một món' },
          ]}
        />
        {type === 'DISCOUNT' ? (
          <Flex vertical gap={8}>
            <InputNumber<number>
              style={{ width: '100%' }}
              min={1000}
              step={1000}
              value={amount}
              onChange={setAmount}
              placeholder="Số tiền giảm"
              formatter={(v) => `${v ?? ''}`.replace(/\B(?=(\d{3})+(?!\d))/g, '.')}
              parser={(v) => Number((v ?? '').replace(/\./g, ''))}
              addonAfter="đ"
            />
            <Flex gap={8}>
              {[5, 10].map((p) => (
                <Button key={p} size="small" onClick={() => setAmount(percent(p))}>
                  {p}% ({money(percent(p))})
                </Button>
              ))}
            </Flex>
          </Flex>
        ) : (
          <Select
            placeholder="Chọn món tặng"
            value={itemId}
            onChange={setItemId}
            options={dishes.map((i) => ({ value: i.id, label: `${i.itemName} x${i.quantity} · ${money(i.unitPrice * i.quantity)}` }))}
          />
        )}
        <Select
          placeholder="Lý do"
          value={reason}
          onChange={setReason}
          options={Object.entries(adjustmentReasonLabel).map(([value, label]) => ({ value, label }))}
        />
        <Input.TextArea
          rows={2}
          maxLength={300}
          value={note}
          onChange={(e) => setNote(e.target.value)}
          placeholder={reason === 'OTHER' ? 'Ghi chú (bắt buộc khi lý do là Khác)' : 'Ghi chú (không bắt buộc)'}
        />
        <Typography.Text type="secondary">
          Tiền món {money(order.subtotal)}. Thu ngân giảm tới 10% tiền món và tối đa 150.000 đ mỗi bill; quá mức thì chờ
          quản lý duyệt.
        </Typography.Text>
        {outcome === 'approval' && <Alert type="warning" showIcon title="Vượt hạn mức: khoản giảm sẽ chờ quản lý duyệt, chưa thanh toán được tới lúc đó" />}
        {outcome === 'over' && <Alert type="error" showIcon title="Tổng giảm vượt tiền món" />}
      </Flex>
    </Modal>
  )
}
