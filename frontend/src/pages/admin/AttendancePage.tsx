import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Button, DatePicker, Form, Input, Modal, Select, Space, Table, Tag, Tooltip, Typography } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import dayjs, { type Dayjs } from 'dayjs'
import { api, errorMessage } from '../../api/client'
import type { AttendanceRecord, StaffMember } from '../../api/types'
import { duration, hhmm, mondayOf } from '../../utils/hr'

interface EditValues {
  checkInAt: Dayjs
  checkOutAt?: Dayjs | null
  reason: string
}

const clockTime = (value: string | null) => (value ? dayjs(value).format('HH:mm') : '')

/** FR-14.3: attendance by day and person; corrections need a reason and are signed (BR-25). */
export default function AttendancePage() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  const [range, setRange] = useState<[Dayjs, Dayjs]>(() => [mondayOf(dayjs()), dayjs()])
  const [employeeId, setEmployeeId] = useState<number | undefined>()
  const [editing, setEditing] = useState<AttendanceRecord | null>(null)
  const [adding, setAdding] = useState(false)
  const from = range[0].format('YYYY-MM-DD')
  const to = range[1].format('YYYY-MM-DD')

  const staff = useQuery({ queryKey: ['schedule-staff'], queryFn: () => api.get<StaffMember[]>('/schedule/staff').then((r) => r.data) })
  const records = useQuery({
    queryKey: ['attendance', from, to, employeeId],
    queryFn: () => api.get<AttendanceRecord[]>('/attendance', { params: { from, to, employeeId } }).then((r) => r.data),
  })

  const onError = (e: unknown) => message.error(errorMessage(e))
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['attendance'] })

  const edit = useMutation({
    mutationFn: (v: EditValues) =>
      api.put(`/attendance/${editing!.id}`, {
        checkInAt: v.checkInAt.toISOString(),
        checkOutAt: v.checkOutAt ? v.checkOutAt.toISOString() : null,
        reason: v.reason,
      }),
    onSuccess: () => {
      message.success('Đã sửa')
      setEditing(null)
      refresh()
    },
    onError,
  })
  const add = useMutation({
    mutationFn: (v: EditValues & { employeeId: number }) =>
      api.post('/attendance', {
        employeeId: v.employeeId,
        checkInAt: v.checkInAt.toISOString(),
        checkOutAt: v.checkOutAt?.toISOString(),
        reason: v.reason,
      }),
    onSuccess: () => {
      message.success('Đã thêm')
      setAdding(false)
      refresh()
    },
    onError,
  })

  const staffOptions = (staff.data ?? []).map((s) => ({ label: s.fullName, value: s.id }))
  const timeField = { showTime: { format: 'HH:mm' }, format: 'DD/MM/YYYY HH:mm', style: { width: '100%' } }

  return (
    <>
      <div className="page-title">
        <Typography.Title level={3}>Chấm công</Typography.Title>
        <Space wrap>
          <Select
            allowClear
            showSearch
            optionFilterProp="label"
            placeholder="Tất cả nhân viên"
            style={{ width: 200 }}
            options={staffOptions}
            value={employeeId}
            onChange={setEmployeeId}
          />
          <DatePicker.RangePicker
            value={range}
            format="DD/MM/YYYY"
            allowClear={false}
            onChange={(value) => value && value[0] && value[1] && setRange([value[0], value[1]])}
          />
          <Button icon={<PlusOutlined />} onClick={() => setAdding(true)}>Thêm bản ghi</Button>
        </Space>
      </div>
      <Table<AttendanceRecord>
        size="small"
        rowKey="id"
        loading={records.isLoading}
        dataSource={records.data ?? []}
        scroll={{ x: true }}
        columns={[
          { title: 'Nhân viên', dataIndex: 'employeeName' },
          { title: 'Ngày', render: (_, r) => dayjs(r.workDate).format('dd DD/MM') },
          {
            title: 'Ca',
            render: (_, r) => (r.shiftName ? `${r.shiftName} ${hhmm(r.shiftStart)}–${hhmm(r.shiftEnd)}` : <Tag>Ngoài lịch</Tag>),
          },
          { title: 'Vào', render: (_, r) => clockTime(r.checkInAt) },
          { title: 'Ra', render: (_, r) => (r.checkOutAt ? clockTime(r.checkOutAt) : <Tag color="gold">Chưa ra ca</Tag>) },
          { title: 'Đi muộn', render: (_, r) => (r.lateMinutes > 0 ? <Tag color="orange">{duration(r.lateMinutes)}</Tag> : '') },
          { title: 'Về sớm', render: (_, r) => (r.earlyMinutes > 0 ? <Tag color="orange">{duration(r.earlyMinutes)}</Tag> : '') },
          { title: 'Giờ làm', render: (_, r) => duration(r.workedMinutes) },
          {
            title: 'Sửa bởi',
            render: (_, r) =>
              r.editedByName ? (
                <Tooltip title={r.editReason}>
                  <Tag color="blue">{r.editedByName}</Tag>
                </Tooltip>
              ) : (
                ''
              ),
          },
          { title: '', render: (_, r) => <Button size="small" onClick={() => setEditing(r)}>Sửa</Button> },
        ]}
      />

      <Modal title={`Sửa chấm công: ${editing?.employeeName ?? ''}`} open={editing !== null} onCancel={() => setEditing(null)} footer={null} destroyOnHidden>
        {editing && (
          <Form
            layout="vertical"
            initialValues={{ checkInAt: dayjs(editing.checkInAt), checkOutAt: editing.checkOutAt ? dayjs(editing.checkOutAt) : null }}
            onFinish={(v: EditValues) => edit.mutate(v)}
          >
            <Form.Item name="checkInAt" label="Giờ vào" rules={[{ required: true }]}>
              <DatePicker {...timeField} />
            </Form.Item>
            <Form.Item name="checkOutAt" label="Giờ ra">
              <DatePicker {...timeField} />
            </Form.Item>
            <Form.Item name="reason" label="Lý do sửa" rules={[{ required: true, max: 300, message: 'Cần ghi lý do' }]}>
              <Input.TextArea rows={2} placeholder="Quên bấm ra ca, bấm nhầm..." />
            </Form.Item>
            <Button type="primary" htmlType="submit" block loading={edit.isPending}>Lưu</Button>
          </Form>
        )}
      </Modal>

      <Modal title="Thêm bản ghi chấm công" open={adding} onCancel={() => setAdding(false)} footer={null} destroyOnHidden>
        <Form layout="vertical" onFinish={(v: EditValues & { employeeId: number }) => add.mutate(v)}>
          <Form.Item name="employeeId" label="Nhân viên" rules={[{ required: true }]}>
            <Select showSearch optionFilterProp="label" options={staffOptions} />
          </Form.Item>
          <Form.Item name="checkInAt" label="Giờ vào" rules={[{ required: true }]}>
            <DatePicker {...timeField} />
          </Form.Item>
          <Form.Item name="checkOutAt" label="Giờ ra" rules={[{ required: true }]}>
            <DatePicker {...timeField} />
          </Form.Item>
          <Form.Item name="reason" label="Lý do" rules={[{ required: true, max: 300, message: 'Cần ghi lý do' }]}>
            <Input.TextArea rows={2} placeholder="Quên chấm công, làm thêm ngoài lịch..." />
          </Form.Item>
          <Typography.Paragraph type="secondary">Bản ghi thêm tay không gắn với ca nên không tính đi muộn, về sớm.</Typography.Paragraph>
          <Button type="primary" htmlType="submit" block loading={add.isPending}>Lưu</Button>
        </Form>
      </Modal>
    </>
  )
}
