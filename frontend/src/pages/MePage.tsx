import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Button, Card, Col, Row, Space, Table, Tag, Typography } from 'antd'
import dayjs from 'dayjs'
import { api, errorMessage } from '../api/client'
import type { AttendanceRecord, ClockStatus, ShiftAssignment } from '../api/types'
import { duration, hhmm } from '../utils/hr'

const clockTime = (value: string | null) => (value ? dayjs(value).format('HH:mm') : '')

/** Clock in and out for today's shift (FR-14.1, BR-25). */
function ClockCard() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  const status = useQuery({
    queryKey: ['me', 'attendance-status'],
    queryFn: () => api.get<ClockStatus>('/me/attendance/status').then((r) => r.data),
    refetchInterval: 60_000,
  })
  const clock = useMutation({
    mutationFn: (action: 'check-in' | 'check-out') => api.post(`/me/attendance/${action}`),
    onSuccess: (_, action) => {
      message.success(action === 'check-in' ? 'Đã vào ca' : 'Đã ra ca')
      queryClient.invalidateQueries({ queryKey: ['me'] })
    },
    onError: (e) => message.error(errorMessage(e)),
  })

  const current = status.data?.current
  const shifts = status.data?.todayShifts ?? []
  return (
    <Card title="Chấm công hôm nay" size="small" loading={status.isLoading}>
      {current ? (
        <Space orientation="vertical">
          <Typography.Text>
            Đang trong ca {current.shiftName ?? ''} từ {clockTime(current.checkInAt)}{' '}
            {current.lateMinutes > 0 && <Tag color="orange">Muộn {duration(current.lateMinutes)}</Tag>}
          </Typography.Text>
          <Button type="primary" danger size="large" loading={clock.isPending} onClick={() => clock.mutate('check-out')}>
            Ra ca
          </Button>
        </Space>
      ) : (
        <Space orientation="vertical">
          {shifts.length === 0 ? (
            <Typography.Text type="secondary">Hôm nay bạn không có ca.</Typography.Text>
          ) : (
            shifts.map((s) => (
              <Typography.Text key={s.assignmentId}>
                {s.shiftName} {hhmm(s.startTime)}–{hhmm(s.endTime)} {s.done && <Tag color="green">Đã chấm</Tag>}
              </Typography.Text>
            ))
          )}
          <Button
            type="primary"
            size="large"
            disabled={!shifts.some((s) => !s.done)}
            loading={clock.isPending}
            onClick={() => clock.mutate('check-in')}
          >
            Vào ca
          </Button>
          <Typography.Text type="secondary">Vào ca được từ 15 phút trước giờ bắt đầu.</Typography.Text>
        </Space>
      )}
    </Card>
  )
}

/** Self-service for every employee: clocking, own schedule and attendance (FR-13.4, FR-14.4). */
export default function MePage() {
  const from = dayjs().format('YYYY-MM-DD')
  const to = dayjs().add(13, 'day').format('YYYY-MM-DD')
  const monthStart = dayjs().startOf('month').format('YYYY-MM-DD')

  const schedule = useQuery({
    queryKey: ['me', 'schedule', from],
    queryFn: () => api.get<ShiftAssignment[]>('/me/schedule', { params: { from, to } }).then((r) => r.data),
  })
  const attendance = useQuery({
    queryKey: ['me', 'attendance', monthStart],
    queryFn: () => api.get<AttendanceRecord[]>('/me/attendance', { params: { from: monthStart, to: from } }).then((r) => r.data),
  })

  return (
    <>
      <div className="page-title">
        <Typography.Title level={3}>Của tôi</Typography.Title>
      </div>
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <ClockCard />
        </Col>
        <Col xs={24} lg={12}>
          <Card title="Lịch làm 2 tuần tới" size="small">
            <Table<ShiftAssignment>
              size="small"
              rowKey="id"
              pagination={false}
              loading={schedule.isLoading}
              dataSource={schedule.data ?? []}
              locale={{ emptyText: 'Chưa được xếp ca' }}
              columns={[
                { title: 'Ngày', render: (_, a) => dayjs(a.workDate).format('dd DD/MM') },
                { title: 'Ca', dataIndex: 'shiftName' },
                { title: 'Giờ', render: (_, a) => `${hhmm(a.startTime)}–${hhmm(a.endTime)}` },
              ]}
            />
          </Card>
        </Col>
        <Col xs={24}>
          <Card title="Chấm công tháng này" size="small">
            <Table<AttendanceRecord>
              size="small"
              rowKey="id"
              pagination={false}
              loading={attendance.isLoading}
              dataSource={attendance.data ?? []}
              locale={{ emptyText: 'Chưa có lượt chấm công' }}
              scroll={{ x: true }}
              columns={[
                { title: 'Ngày', render: (_, r) => dayjs(r.workDate).format('dd DD/MM') },
                { title: 'Ca', render: (_, r) => r.shiftName ?? 'Ngoài lịch' },
                { title: 'Vào', render: (_, r) => clockTime(r.checkInAt) },
                { title: 'Ra', render: (_, r) => (r.checkOutAt ? clockTime(r.checkOutAt) : <Tag color="gold">Đang trong ca</Tag>) },
                { title: 'Đi muộn', render: (_, r) => (r.lateMinutes > 0 ? duration(r.lateMinutes) : '') },
                { title: 'Giờ làm', render: (_, r) => duration(r.workedMinutes) },
                { title: 'Ghi chú', render: (_, r) => (r.editedByName ? `${r.editedByName} sửa: ${r.editReason}` : '') },
              ]}
            />
          </Card>
        </Col>
      </Row>
    </>
  )
}
