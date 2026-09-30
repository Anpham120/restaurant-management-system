import { Button, Empty, Flex, Input, Typography } from 'antd'
import { MinusOutlined, PlusOutlined } from '@ant-design/icons'
import { money } from '../utils/format'
import type { Cart } from './useCart'

/** Chosen dishes with quantity and a note per line (e.g. "ít cay"), plus the send button. */
export default function CartPanel({
  cart,
  sendLabel,
  sending,
  onSend,
}: {
  cart: Cart
  sendLabel: string
  sending: boolean
  onSend: () => void
}) {
  if (cart.lines.length === 0) return <Empty description="Chưa chọn món" image={Empty.PRESENTED_IMAGE_SIMPLE} />
  return (
    <Flex vertical gap={8}>
      {cart.lines.map((line) => (
        <div key={line.item.id}>
          <Flex justify="space-between" align="center" gap={8}>
            <Typography.Text strong>{line.item.name}</Typography.Text>
            <Flex align="center" gap={6}>
              <Button size="small" icon={<MinusOutlined />} onClick={() => cart.remove(line.item)} aria-label="Bớt" />
              <span>{line.quantity}</span>
              <Button size="small" icon={<PlusOutlined />} onClick={() => cart.add(line.item)} aria-label="Thêm" />
            </Flex>
          </Flex>
          <Input
            size="small"
            placeholder="Ghi chú (ít cay, không hành...)"
            maxLength={300}
            value={line.note}
            onChange={(e) => cart.setNote(line.item.id, e.target.value)}
            style={{ marginTop: 4 }}
          />
        </div>
      ))}
      <Flex justify="space-between">
        <Typography.Text>Tạm tính</Typography.Text>
        <Typography.Text strong>{money(cart.total)}</Typography.Text>
      </Flex>
      <Button type="primary" size="large" block loading={sending} onClick={onSend}>
        {sendLabel} ({cart.count})
      </Button>
    </Flex>
  )
}
