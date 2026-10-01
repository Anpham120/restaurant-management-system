import { useState } from 'react'
import { Button, Checkbox, Flex, InputNumber, Modal, Segmented, Typography } from 'antd'
import type { Order } from '@/shared/api/types'
import { money, moneyInputProps } from '@/shared/utils/format'
import { billLines } from '../utils/bill'
import { shareOfDishes, splitEvenly } from '../utils/split'

type Mode = 'EVEN' | 'DISHES' | 'AMOUNT'

interface Props {
  order: Order
  onClose: () => void
  /** Takes this part in cash, or asks for it by VietQR, through the usual cashier screens. */
  onCash: (part: number) => void
  onTransfer: (part: number) => void
}

/** FR-08.12, BR-43: works out the next part of a split bill; the parts always add up to the bill. */
export default function SplitPaymentModal({ order, onClose, onCash, onTransfer }: Props) {
  const [mode, setMode] = useState<Mode>('EVEN')
  const [people, setPeople] = useState(2)
  const [picked, setPicked] = useState<number[]>([])
  const [amount, setAmount] = useState<number | null>(null)
  const lines = billLines(order.items)
  const parts = splitEvenly(order.due, people)

  const part =
    mode === 'EVEN'
      ? parts[0]
      : mode === 'DISHES'
        ? picked.length === 0
          ? 0
          : shareOfDishes(lines.filter((l) => picked.includes(l.id)).reduce((sum, l) => sum + l.amount, 0), order)
        : (amount ?? 0)
  const valid = part > 0 && part <= order.due

  return (
    <Modal title="Tách bill" open onCancel={onClose} footer={null} destroyOnHidden>
      <Flex vertical gap={12}>
        <Typography.Text>
          Còn phải thu: <Typography.Text strong>{money(order.due)}</Typography.Text>
        </Typography.Text>
        <Segmented<Mode>
          block
          value={mode}
          onChange={setMode}
          options={[
            { value: 'EVEN', label: 'Chia đều' },
            { value: 'DISHES', label: 'Theo món' },
            { value: 'AMOUNT', label: 'Số tiền' },
          ]}
        />
        {mode === 'EVEN' && (
          <>
            <Flex align="center" gap={8}>
              <span>Số người còn trả</span>
              <InputNumber min={2} max={20} value={people} onChange={(v) => setPeople(v ?? 2)} />
            </Flex>
            <Typography.Text type="secondary">{parts.map((p) => money(p)).join(' + ')}</Typography.Text>
          </>
        )}
        {mode === 'DISHES' && (
          <Checkbox.Group
            value={picked}
            onChange={(v) => setPicked(v as number[])}
            options={lines.map((l) => ({ value: l.id, label: `${l.itemName} × ${l.quantity} · ${money(l.amount)}` }))}
            style={{ display: 'flex', flexDirection: 'column', gap: 4 }}
          />
        )}
        {mode === 'AMOUNT' && (
          <InputNumber<number>
            min={1}
            max={order.due}
            step={10_000}
            value={amount}
            onChange={setAmount}
            placeholder="Số tiền thu lần này"
            suffix="đ"
            style={{ width: '100%' }}
            {...moneyInputProps}
          />
        )}
        <Typography.Title level={4} style={{ margin: 0 }}>
          Thu lần này: {money(part)}
        </Typography.Title>
        {valid && <Typography.Text type="secondary">Sau phần này còn {money(order.due - part)}</Typography.Text>}
        <Flex gap={8}>
          <Button size="large" style={{ flex: 1 }} disabled={!valid} onClick={() => onCash(part)}>
            Tiền mặt
          </Button>
          <Button size="large" type="primary" style={{ flex: 1 }} disabled={!valid} onClick={() => onTransfer(part)}>
            VietQR
          </Button>
        </Flex>
      </Flex>
    </Modal>
  )
}
