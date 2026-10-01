import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Button, Modal, Spin, Typography } from 'antd'
import { api, errorMessage } from '@/shared/api/client'
import type { Reservation, ReservationConfirmation } from '@/shared/api/types'
import { time } from '@/shared/utils/format'

/** FR-18.2: the confirmation to send by Zalo or SMS; marking it sent keeps the text and the time as evidence. */
export default function ConfirmationModal({ booking, onClose }: { booking: Reservation | null; onClose: () => void }) {
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  const confirmation = useQuery({
    queryKey: ['reservation-confirmation', booking?.id],
    queryFn: () => api.get<ReservationConfirmation>(`/reservations/${booking!.id}/confirmation`).then((r) => r.data),
    enabled: booking !== null,
    staleTime: 0,
  })
  const sent = useMutation({
    mutationFn: () => api.post<ReservationConfirmation>(`/reservations/${booking!.id}/confirmation`).then((r) => r.data),
    onSuccess: () => {
      message.success('Đã lưu tin xác nhận đã gửi')
      queryClient.invalidateQueries({ queryKey: ['reservations'] })
      onClose()
    },
    onError: (e) => message.error(errorMessage(e)),
  })

  return (
    <Modal title={booking ? `Tin xác nhận ${booking.code}` : ''} open={booking !== null} onCancel={onClose} footer={null} destroyOnHidden>
      {confirmation.isLoading || !confirmation.data ? (
        <Spin />
      ) : (
        <>
          <Typography.Paragraph copyable={{ text: confirmation.data.text }} style={{ whiteSpace: 'pre-wrap' }}>
            {confirmation.data.text}
          </Typography.Paragraph>
          <Typography.Paragraph type="secondary">
            {confirmation.data.sentAt ? `Đã gửi lúc ${time(confirmation.data.sentAt)}. ` : ''}Sao chép, gửi cho khách qua Zalo hoặc SMS, rồi
            bấm nút dưới để lưu lại tin đã gửi.
          </Typography.Paragraph>
          <Button type="primary" block loading={sent.isPending} onClick={() => sent.mutate()}>
            Đã gửi cho khách
          </Button>
        </>
      )}
    </Modal>
  )
}
