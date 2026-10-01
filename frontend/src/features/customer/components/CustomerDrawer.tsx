import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Button, Drawer, Flex, Form, Input, Statistic, Table, Tag, Typography } from 'antd'
import dayjs from 'dayjs'
import { api, errorMessage } from '@/shared/api/client'
import type { Customer, CustomerBooking, CustomerDetail, CustomerVisit } from '@/shared/api/types'
import { money } from '@/shared/utils/format'
import { statusColor, statusLabel } from '@/features/reservation/utils/reservation'
import ConsentControls from './ConsentControls'

interface Props {
  id: number | null
  onClose: () => void
}

/** FR-19.1, FR-19.3, FR-19.4: one guest, their paid visits and bookings; the manager edits the name and note. */
export default function CustomerDrawer({ id, onClose }: Props) {
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  const detail = useQuery({
    queryKey: ['customer', id],
    queryFn: () => api.get<CustomerDetail>(`/customers/${id}`).then((r) => r.data),
    enabled: id !== null,
  })

  const changed = (c: Customer) => {
    queryClient.setQueryData<CustomerDetail>(['customer', c.id], (d) => (d ? { ...d, customer: c } : d))
    queryClient.invalidateQueries({ queryKey: ['customers'] })
  }
  const save = useMutation({
    mutationFn: (values: { name?: string; note?: string }) => api.put<Customer>(`/customers/${id}`, values).then((r) => r.data),
    onSuccess: (c) => {
      message.success('Đã lưu')
      changed(c)
    },
    onError: (e) => message.error(errorMessage(e)),
  })

  const d = detail.data
  const c = d?.customer
  return (
    <Drawer
      title={c ? (c.name ?? c.phone) : 'Khách hàng'}
      open={id !== null}
      onClose={onClose}
      size="min(100vw, 640px)"
      loading={detail.isLoading}
      destroyOnHidden
    >
      {d && c && (
        <Flex vertical gap={16}>
          <Flex gap={32} wrap>
            <Statistic title="Số lần ghé" value={c.visits} />
            <Statistic title="Tổng chi" value={money(c.spent)} />
            <Statistic title="Lần ghé gần nhất" value={c.lastVisitAt ? dayjs(c.lastVisitAt).format('DD/MM/YYYY') : 'Chưa có'} />
          </Flex>
          <Form key={c.id} layout="vertical" initialValues={{ name: c.name ?? '', note: c.note ?? '' }} onFinish={save.mutate}>
            <Form.Item label="Số điện thoại">
              <Typography.Text strong>{c.phone}</Typography.Text>
            </Form.Item>
            <Form.Item name="name" label="Tên">
              <Input maxLength={100} />
            </Form.Item>
            <Form.Item name="note" label="Ghi chú">
              <Input.TextArea maxLength={300} rows={2} />
            </Form.Item>
            <Button htmlType="submit" loading={save.isPending}>
              Lưu
            </Button>
          </Form>
          <div>
            <Typography.Title level={5}>Nhận tin</Typography.Title>
            <ConsentControls key={c.id} customer={c} onChanged={changed} />
          </div>
          <div>
            <Typography.Title level={5}>Lần ghé</Typography.Title>
            <Table<CustomerVisit>
              size="small"
              rowKey="orderId"
              pagination={false}
              dataSource={d.visits}
              locale={{ emptyText: 'Chưa có đơn đã thanh toán' }}
              scroll={{ x: 'max-content' }}
              columns={[
                { title: 'Ngày', render: (_, v) => dayjs(v.closedAt).format('DD/MM/YYYY HH:mm') },
                { title: 'Đơn', render: (_, v) => `#${v.orderId}` },
                { title: 'Bàn', render: (_, v) => v.tableName ?? 'Mang về' },
                { title: 'Đã trả', render: (_, v) => money(v.paid) },
              ]}
            />
          </div>
          <div>
            <Typography.Title level={5}>Đặt bàn</Typography.Title>
            <Table<CustomerBooking>
              size="small"
              rowKey="id"
              pagination={false}
              dataSource={d.bookings}
              locale={{ emptyText: 'Chưa đặt bàn' }}
              scroll={{ x: 'max-content' }}
              columns={[
                { title: 'Thời gian', render: (_, b) => dayjs(b.reservedAt).format('DD/MM/YYYY HH:mm') },
                { title: 'Mã', dataIndex: 'code' },
                { title: 'Số khách', dataIndex: 'guestCount' },
                { title: 'Trạng thái', render: (_, b) => <Tag color={statusColor[b.status]}>{statusLabel[b.status]}</Tag> },
              ]}
            />
          </div>
        </Flex>
      )}
    </Drawer>
  )
}
