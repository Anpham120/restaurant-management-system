import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Button, Card, Col, DatePicker, Descriptions, Form, Input, Modal, Row, Select, Space, Table, Tag, Typography } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { api, errorMessage } from '@/shared/api/client'
import type { AttendanceRecord, ClockStatus, LeaveRequest, LeaveType, MyPayslip, Payslip, ShiftAssignment } from '@/shared/api/types'
import { money, payText } from '@/shared/utils/format'
import { duration } from '@/features/attendance/utils/attendance'
import { leaveDays, leaveStatusColor, leaveStatusLabel, leaveTypeLabel } from '@/features/leave/utils/leave'
import { periodLabel } from '@/features/payroll/utils/payroll'
import { hhmm } from '@/features/schedule/utils/schedule'

const LEAVE_TYPES = (Object.keys(leaveTypeLabel) as LeaveType[]).map((t) => ({ label: leaveTypeLabel[t], value: t }))

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

/** Ask for leave and follow the requests (FR-13.5, BR-24). */
function LeaveCard() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  const [open, setOpen] = useState(false)
  const leaves = useQuery({ queryKey: ['me', 'leave'], queryFn: () => api.get<LeaveRequest[]>('/me/leave-requests').then((r) => r.data) })

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['me'] })
  const onError = (e: unknown) => message.error(errorMessage(e))
  const create = useMutation({
    mutationFn: (v: { range: [Dayjs, Dayjs]; type: LeaveType; reason: string }) =>
      api.post('/me/leave-requests', {
        fromDate: v.range[0].format('YYYY-MM-DD'),
        toDate: v.range[1].format('YYYY-MM-DD'),
        type: v.type,
        reason: v.reason,
      }),
    onSuccess: () => {
      message.success('Đã gửi đơn nghỉ')
      setOpen(false)
      refresh()
    },
    onError,
  })
  const cancel = useMutation({ mutationFn: (id: number) => api.post(`/me/leave-requests/${id}/cancel`), onSuccess: refresh, onError })

  return (
    <Card title="Nghỉ phép" size="small" extra={<Button onClick={() => setOpen(true)}>Xin nghỉ</Button>}>
      <Table<LeaveRequest>
        size="small"
        rowKey="id"
        pagination={{ pageSize: 5 }}
        loading={leaves.isLoading}
        dataSource={leaves.data ?? []}
        locale={{ emptyText: 'Chưa có đơn nghỉ' }}
        columns={[
          { title: 'Ngày', render: (_, l) => leaveDays(l) },
          { title: 'Loại', render: (_, l) => leaveTypeLabel[l.type] },
          {
            title: 'Trạng thái',
            render: (_, l) => (
              <>
                <Tag color={leaveStatusColor[l.status]}>{leaveStatusLabel[l.status]}</Tag>
                {l.decisionNote && <Typography.Text type="secondary">{l.decisionNote}</Typography.Text>}
              </>
            ),
          },
          {
            title: '',
            render: (_, l) =>
              l.status === 'PENDING' && (
                <Button size="small" loading={cancel.isPending} onClick={() => cancel.mutate(l.id)}>Huỷ đơn</Button>
              ),
          },
        ]}
      />
      <Modal title="Xin nghỉ" open={open} onCancel={() => setOpen(false)} footer={null} destroyOnHidden>
        <Form layout="vertical" initialValues={{ type: 'PAID' }} onFinish={(v) => create.mutate(v)}>
          <Form.Item name="range" label="Từ ngày, đến ngày" rules={[{ required: true }]}>
            <DatePicker.RangePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="type" label="Loại nghỉ" rules={[{ required: true }]}>
            <Select options={LEAVE_TYPES} />
          </Form.Item>
          <Form.Item name="reason" label="Lý do" rules={[{ required: true, max: 300 }]}>
            <Input.TextArea rows={2} />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={create.isPending}>Gửi đơn</Button>
        </Form>
      </Modal>
    </Card>
  )
}

/** Own finalized payslips, nobody else's (FR-15.4, BR-27). */
function PayslipCard() {
  const [viewing, setViewing] = useState<number | null>(null)
  const list = useQuery({ queryKey: ['me', 'payslips'], queryFn: () => api.get<MyPayslip[]>('/me/payslips').then((r) => r.data) })
  const slip = useQuery({
    queryKey: ['me', 'payslip', viewing],
    queryFn: () => api.get<Payslip>(`/me/payslips/${viewing}`).then((r) => r.data),
    enabled: viewing !== null,
  })
  const s = slip.data

  return (
    <Card title="Phiếu lương" size="small">
      <Table<MyPayslip>
        size="small"
        rowKey="id"
        pagination={{ pageSize: 6 }}
        loading={list.isLoading}
        dataSource={list.data ?? []}
        locale={{ emptyText: 'Chưa có phiếu lương đã chốt' }}
        columns={[
          { title: 'Tháng', render: (_, p) => periodLabel(p.period) },
          { title: 'Thực nhận', render: (_, p) => money(p.netAmount) },
          { title: '', render: (_, p) => <Button size="small" onClick={() => setViewing(p.id)}>Xem</Button> },
        ]}
      />
      <Modal
        title={s ? `Phiếu lương tháng ${periodLabel(s.period)}` : 'Phiếu lương'}
        open={viewing !== null}
        onCancel={() => setViewing(null)}
        footer={null}
      >
        {s && (
          <Descriptions
            column={1}
            size="small"
            bordered
            items={[
              { key: 'rate', label: 'Mức lương', children: payText(s.payType, s.payRate) },
              {
                key: 'work',
                label: 'Công',
                children:
                  s.payType === 'HOURLY'
                    ? duration(s.workedMinutes)
                    : `${s.workDays} ngày công, ${s.paidLeaveDays} ngày nghỉ có lương`,
              },
              { key: 'base', label: 'Lương theo công', children: money(s.baseAmount) },
              ...s.adjustments.map((a) => ({
                key: `adjustment-${a.id}`,
                label: a.amount > 0 ? 'Thưởng' : 'Phạt',
                children: `${money(a.amount)} (${a.reason})`,
              })),
              { key: 'net', label: 'Thực nhận', children: <strong>{money(s.netAmount)}</strong> },
            ]}
          />
        )}
      </Modal>
    </Card>
  )
}

/** Self-service for every employee: clocking, schedule, attendance, leave and payslips (FR-13.4, FR-13.5, FR-14.4, FR-15.4). */
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
        <Col xs={24} lg={12}>
          <LeaveCard />
        </Col>
        <Col xs={24} lg={12}>
          <PayslipCard />
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
