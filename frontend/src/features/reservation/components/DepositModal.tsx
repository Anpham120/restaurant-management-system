import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Button, Modal, Popconfirm, Result, Spin, Typography } from 'antd'
import { api, errorMessage } from '@/shared/api/client'
import type { DepositInstruction, Reservation } from '@/shared/api/types'
import { useAuth } from '@/features/auth/context/AuthContext'
import TransferQr from '@/features/payment/components/TransferQr'
import { hasRole, money } from '@/shared/utils/format'

/**
 * FR-18.3: VietQR for the deposit, to send to the guest. The webhook confirms it when the right amount comes in with
 * the booking code; a manager can confirm it by hand after checking the bank app (BR-42).
 */
export default function DepositModal({ booking, onClose }: { booking: Reservation | null; onClose: () => void }) {
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  const { user } = useAuth()
  const paid = booking?.depositPaidAt != null
  const instruction = useQuery({
    queryKey: ['reservation-deposit', booking?.id],
    queryFn: () => api.post<DepositInstruction>(`/reservations/${booking!.id}/deposit`).then((r) => r.data),
    enabled: booking !== null && !paid,
    staleTime: Infinity,
    retry: false,
  })
  const confirm = useMutation({
    mutationFn: () => api.post(`/reservations/${booking!.id}/deposit/confirm`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['reservations'] }),
    onError: (e) => message.error(errorMessage(e)),
  })

  return (
    <Modal title={booking ? `Cọc ${booking.code}` : ''} open={booking !== null} onCancel={onClose} footer={null} destroyOnHidden>
      {paid ? (
        <Result status="success" title={`Đã nhận cọc ${money(booking!.depositAmount)}`} subTitle="Cọc sẽ được trừ vào bill khi khách tới." />
      ) : instruction.isError ? (
        <Typography.Text type="danger">{errorMessage(instruction.error)}</Typography.Text>
      ) : !instruction.data ? (
        <Spin />
      ) : (
        <>
          <TransferQr instruction={instruction.data} />
          <Typography.Paragraph type="secondary" style={{ marginTop: 12, textAlign: 'center' }}>
            Gửi ảnh mã này cho khách. Tiền về đúng số và đúng nội dung thì cọc tự xác nhận.
          </Typography.Paragraph>
          {hasRole(user?.role, 'MANAGER') && (
            <Popconfirm title="Đã thấy tiền cọc về trong app ngân hàng?" onConfirm={() => confirm.mutate()}>
              <Button block loading={confirm.isPending}>
                Xác nhận tay
              </Button>
            </Popconfirm>
          )}
        </>
      )}
    </Modal>
  )
}
