import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Button, Modal, Select, Typography } from 'antd'
import { useNavigate } from 'react-router'
import { api, errorMessage } from '@/shared/api/client'
import type { DiningTable, Order, Reservation } from '@/shared/api/types'
import { money } from '@/shared/utils/format'

/** FR-18.4: the guests arrive; their order opens at a free table, the planned one when it is free. */
export default function SeatModal({ booking, onClose }: { booking: Reservation | null; onClose: () => void }) {
  return (
    <Modal title={booking ? `Nhận khách ${booking.code}` : ''} open={booking !== null} onCancel={onClose} footer={null} destroyOnHidden>
      {booking && <SeatForm booking={booking} />}
    </Modal>
  )
}

function SeatForm({ booking }: { booking: Reservation }) {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const { message } = App.useApp()
  const tables = useQuery({ queryKey: ['tables'], queryFn: () => api.get<DiningTable[]>('/tables').then((r) => r.data) })
  const free = (tables.data ?? []).filter((t) => t.openOrderId === null)
  const plannedFree = free.some((t) => t.id === booking.tableId)
  const [tableId, setTableId] = useState<number>()
  const chosen = tableId ?? (plannedFree ? (booking.tableId ?? undefined) : undefined)

  const seat = useMutation({
    mutationFn: () => api.post<Order>(`/reservations/${booking.id}/seat`, { tableId: chosen }).then((r) => r.data),
    onSuccess: (order) => {
      queryClient.invalidateQueries({ queryKey: ['reservations'] })
      navigate(`/orders/${order.id}`)
    },
    onError: (e) => message.error(errorMessage(e)),
  })

  return (
    <>
      <Typography.Paragraph>
        {booking.guestName}, {booking.guestCount} khách
        {booking.depositPaidAt ? `, đã cọc ${money(booking.depositAmount)}: cọc được trừ vào bill.` : '.'}
      </Typography.Paragraph>
      {booking.tableId !== null && !plannedFree && !tables.isLoading && (
        <Typography.Paragraph type="warning">Bàn dự kiến {booking.tableName} đang có khách, chọn bàn khác.</Typography.Paragraph>
      )}
      <Select
        style={{ width: '100%', marginBottom: 16 }}
        placeholder="Chọn bàn trống"
        loading={tables.isLoading}
        value={chosen}
        onChange={setTableId}
        options={free.map((t) => ({ value: t.id, label: [t.name, t.area, `${t.seats} chỗ`].filter(Boolean).join(' · ') }))}
      />
      <Button type="primary" block disabled={chosen === undefined} loading={seat.isPending} onClick={() => seat.mutate()}>
        Nhận khách, mở đơn
      </Button>
    </>
  )
}
