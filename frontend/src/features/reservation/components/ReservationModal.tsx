import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Button, DatePicker, Form, Input, InputNumber, Modal, Select, Typography } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { api, errorMessage } from '@/shared/api/client'
import type { DiningTable, Reservation } from '@/shared/api/types'
import { moneyInputProps } from '@/shared/utils/format'

interface Values {
  guestName: string
  phone: string
  reservedAt: Dayjs
  guestCount: number
  tableId?: number | null
  depositAmount: number
  note?: string
}

/** FR-18.1: a new booking, or one still waiting for its guests. A deposit received keeps its amount (BR-42). */
export default function ReservationModal({
  editing,
  day,
  onClose,
}: {
  editing: { record: Reservation | null } | null
  day: Dayjs
  onClose: () => void
}) {
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  const record = editing?.record ?? null
  const tables = useQuery({
    queryKey: ['tables'],
    queryFn: () => api.get<DiningTable[]>('/tables').then((r) => r.data),
    enabled: editing !== null,
  })

  const save = useMutation({
    mutationFn: (v: Values) => {
      const body = { ...v, tableId: v.tableId ?? null, reservedAt: v.reservedAt.toISOString() }
      return record ? api.put(`/reservations/${record.id}`, body) : api.post('/reservations', body)
    },
    onSuccess: () => {
      message.success(record ? 'Đã lưu booking' : 'Đã đặt bàn')
      queryClient.invalidateQueries({ queryKey: ['reservations'] })
      onClose()
    },
    onError: (e) => message.error(errorMessage(e)),
  })

  const initial: Partial<Values> = record
    ? { ...record, reservedAt: dayjs(record.reservedAt), note: record.note ?? undefined }
    : { reservedAt: day.hour(19).minute(0).second(0).millisecond(0), guestCount: 2, depositAmount: 0 }

  return (
    <Modal title={record ? `Sửa booking ${record.code}` : 'Đặt bàn'} open={editing !== null} onCancel={onClose} footer={null} destroyOnHidden>
      <Form layout="vertical" initialValues={initial} onFinish={(v: Values) => save.mutate(v)}>
        <Form.Item name="guestName" label="Tên khách" rules={[{ required: true, whitespace: true, max: 100 }]}>
          <Input />
        </Form.Item>
        <Form.Item name="phone" label="Số điện thoại" rules={[{ required: true, whitespace: true, max: 20 }]}>
          <Input inputMode="tel" />
        </Form.Item>
        <Form.Item name="reservedAt" label="Giờ tới" rules={[{ required: true }]}>
          <DatePicker showTime={{ format: 'HH:mm', minuteStep: 15 }} format="HH:mm DD/MM/YYYY" style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="guestCount" label="Số khách" rules={[{ required: true }]}>
          <InputNumber min={1} max={200} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="tableId" label="Bàn dự kiến">
          <Select
            allowClear
            showSearch
            optionFilterProp="label"
            placeholder="Chưa chọn"
            loading={tables.isLoading}
            options={(tables.data ?? []).map((t) => ({ value: t.id, label: [t.name, t.area, `${t.seats} chỗ`].filter(Boolean).join(' · ') }))}
          />
        </Form.Item>
        <Form.Item
          name="depositAmount"
          label="Tiền cọc"
          rules={[{ required: true }]}
          extra={record?.depositPaidAt ? 'Đã nhận cọc nên không đổi số tiền được.' : '0 là không cọc.'}
        >
          <InputNumber min={0} max={1_000_000_000} step={100_000} suffix="đ" disabled={!!record?.depositPaidAt} style={{ width: '100%' }} {...moneyInputProps} />
        </Form.Item>
        <Form.Item name="note" label="Ghi chú" rules={[{ max: 300 }]}>
          <Input placeholder="Sinh nhật, cần ghế trẻ em..." />
        </Form.Item>
        {!record && <Typography.Paragraph type="secondary">Mã booking cũng là nội dung chuyển khoản cọc.</Typography.Paragraph>}
        <Button type="primary" htmlType="submit" block loading={save.isPending}>
          Lưu
        </Button>
      </Form>
    </Modal>
  )
}
