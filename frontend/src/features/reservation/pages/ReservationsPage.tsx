import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Button, DatePicker, Flex, Popconfirm, Space, Statistic, Table, Tag, Typography } from 'antd'
import { CheckOutlined, PlusOutlined } from '@ant-design/icons'
import { Link } from 'react-router'
import dayjs, { type Dayjs } from 'dayjs'
import { api, errorMessage } from '@/shared/api/client'
import type { Reservation, ReservationDay } from '@/shared/api/types'
import { money } from '@/shared/utils/format'
import ConfirmationModal from '../components/ConfirmationModal'
import DepositModal from '../components/DepositModal'
import ReservationModal from '../components/ReservationModal'
import SeatModal from '../components/SeatModal'
import { depositColor, depositLabel, depositState, statusColor, statusLabel } from '../utils/reservation'

/** FR-18: bookings of a day, their confirmation, their deposit by VietQR, and seating the guests (BR-42). */
export default function ReservationsPage() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  const [day, setDay] = useState<Dayjs>(dayjs())
  const [editing, setEditing] = useState<{ record: Reservation | null } | null>(null)
  const [confirming, setConfirming] = useState<Reservation | null>(null)
  const [depositOf, setDepositOf] = useState<number | null>(null)
  const [seating, setSeating] = useState<Reservation | null>(null)
  const date = day.format('YYYY-MM-DD')

  const reservations = useQuery({
    queryKey: ['reservations', date],
    queryFn: () => api.get<ReservationDay>('/reservations', { params: { date } }).then((r) => r.data),
  })
  const list = reservations.data?.reservations ?? []

  const end = useMutation({
    mutationFn: ({ id, how }: { id: number; how: 'cancel' | 'no-show' }) => api.post(`/reservations/${id}/${how}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['reservations'] }),
    onError: (e) => message.error(errorMessage(e)),
  })

  return (
    <>
      <div className="page-title">
        <Typography.Title level={3}>Đặt bàn</Typography.Title>
        <Flex gap={8} wrap>
          <DatePicker value={day} format="DD/MM/YYYY" allowClear={false} onChange={(value) => value && setDay(value)} />
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing({ record: null })}>
            Đặt bàn
          </Button>
        </Flex>
      </div>
      <Flex gap={32} wrap style={{ marginBottom: 16 }}>
        <Statistic title="Booking trong ngày" value={list.filter((r) => r.status !== 'CANCELLED').length} />
        <Statistic title="Khách dự kiến" value={list.filter((r) => r.status === 'BOOKED').reduce((n, r) => n + r.guestCount, 0)} />
        {/* BR-42: deposits received and not yet taken off a bill, whatever their day. */}
        <Statistic title="Cọc đang giữ" value={money(reservations.data?.depositsHeld ?? 0)} />
      </Flex>
      <Table<Reservation>
        size="small"
        rowKey="id"
        loading={reservations.isLoading}
        dataSource={list}
        pagination={false}
        scroll={{ x: 1100 }}
        columns={[
          { title: 'Giờ', width: 64, render: (_, r) => dayjs(r.reservedAt).format('HH:mm') },
          { title: 'Mã', dataIndex: 'code' },
          {
            title: 'Khách',
            render: (_, r) => (
              <>
                <div>{r.guestName}</div>
                <Typography.Text type="secondary">{r.phone}</Typography.Text>
              </>
            ),
          },
          { title: 'Số khách', dataIndex: 'guestCount', width: 80 },
          { title: 'Bàn', render: (_, r) => r.tableName ?? '' },
          {
            title: 'Cọc',
            render: (_, r) => {
              const state = depositState(r)
              return (
                <Space size={4} wrap>
                  {r.depositAmount > 0 && money(r.depositAmount)}
                  <Tag color={depositColor[state]}>{depositLabel[state]}</Tag>
                </Space>
              )
            },
          },
          { title: 'Trạng thái', render: (_, r) => <Tag color={statusColor[r.status]}>{statusLabel[r.status]}</Tag> },
          { title: 'Ghi chú', dataIndex: 'note' },
          {
            title: '',
            render: (_, r) => (
              <Space wrap>
                {r.status === 'SEATED' && r.orderId && <Link to={`/orders/${r.orderId}`}>Đơn #{r.orderId}</Link>}
                {r.status === 'BOOKED' && (
                  <>
                    <Button size="small" type="primary" onClick={() => setSeating(r)}>
                      Nhận khách
                    </Button>
                    <Button size="small" icon={r.confirmationSentAt ? <CheckOutlined /> : undefined} onClick={() => setConfirming(r)}>
                      Tin xác nhận
                    </Button>
                    {depositState(r) === 'WAITING' && (
                      <Button size="small" onClick={() => setDepositOf(r.id)}>
                        Mã cọc
                      </Button>
                    )}
                    <Button size="small" onClick={() => setEditing({ record: r })}>
                      Sửa
                    </Button>
                    <Popconfirm title="Huỷ booking này?" okText="Huỷ booking" cancelText="Không" onConfirm={() => end.mutate({ id: r.id, how: 'cancel' })}>
                      <Button size="small" danger>
                        Huỷ
                      </Button>
                    </Popconfirm>
                    <Popconfirm title="Khách không tới?" okText="Ghi không tới" cancelText="Không" onConfirm={() => end.mutate({ id: r.id, how: 'no-show' })}>
                      <Button size="small">Không tới</Button>
                    </Popconfirm>
                  </>
                )}
              </Space>
            ),
          },
        ]}
      />

      <ReservationModal editing={editing} day={day} onClose={() => setEditing(null)} />
      <ConfirmationModal booking={confirming} onClose={() => setConfirming(null)} />
      <DepositModal booking={list.find((r) => r.id === depositOf) ?? null} onClose={() => setDepositOf(null)} />
      <SeatModal booking={seating} onClose={() => setSeating(null)} />
    </>
  )
}
