import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { App, Flex, Modal, Select, Typography } from 'antd'
import { api, errorMessage } from '@/shared/api/client'
import type { DiningTable, Order } from '@/shared/api/types'

interface Props {
  order: Order
  onClose: () => void
  onMoved: (order: Order) => void
}

/**
 * FR-04.5, FR-04.6: the tables an order holds. Adding one puts tables together, replacing them moves the order;
 * the first is the main table and the bill stays (BR-36). Render it only while open.
 */
export default function MoveTablesModal({ order, onClose, onMoved }: Props) {
  const { message } = App.useApp()
  const [chosen, setChosen] = useState<number[]>(order.tableIds)
  const tables = useQuery({ queryKey: ['tables'], queryFn: () => api.get<DiningTable[]>('/tables').then((r) => r.data) })
  const choices = (tables.data ?? []).filter((t) => t.status === 'AVAILABLE' || order.tableIds.includes(t.id))
  const names = chosen.map((id) => choices.find((t) => t.id === id)?.name ?? '').filter(Boolean)

  const move = useMutation({
    mutationFn: () => api.post<Order>(`/orders/${order.id}/tables`, { tableIds: chosen }).then((r) => r.data),
    onSuccess: (moved) => {
      message.success(`Đơn đã ở ${moved.tableName}`)
      onMoved(moved)
      onClose()
    },
    onError: (e) => message.error(errorMessage(e)),
  })

  return (
    <Modal
      open
      title="Chuyển, ghép bàn"
      okText="Lưu"
      okButtonProps={{ disabled: chosen.length === 0 || chosen.length > 10 }}
      confirmLoading={move.isPending}
      onOk={() => move.mutate()}
      onCancel={onClose}
    >
      <Flex vertical gap={8}>
        <Typography.Text type="secondary">
          Thêm bàn trống để ghép; bỏ bàn cũ và chọn bàn mới để chuyển. Bàn đầu tiên là bàn chính. Món, giảm giá và mã
          chuyển khoản giữ nguyên.
        </Typography.Text>
        <Select
          mode="multiple"
          placeholder="Chọn bàn"
          loading={tables.isLoading}
          value={chosen}
          onChange={setChosen}
          options={choices.map((t) => ({ value: t.id, label: t.area ? `${t.name} · ${t.area}` : t.name }))}
        />
        {names.length > 0 && (
          <Typography.Text>
            Đơn sẽ ở: <Typography.Text strong>{names.join(' + ')}</Typography.Text>
          </Typography.Text>
        )}
      </Flex>
    </Modal>
  )
}
