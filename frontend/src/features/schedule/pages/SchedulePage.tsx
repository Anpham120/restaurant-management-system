import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Button, DatePicker, Drawer, Form, Input, Popconfirm, Select, Space, Switch, Table, Tag, TimePicker, Typography } from 'antd'
import { CopyOutlined, LeftOutlined, RightOutlined, SettingOutlined } from '@ant-design/icons'
import dayjs, { type Dayjs } from 'dayjs'
import { api, errorMessage } from '@/shared/api/client'
import type { Role, ShiftAssignment, StaffMember, WorkShift } from '@/shared/api/types'
import { roleLabel } from '@/shared/utils/format'
import { hhmm, mondayOf, timeOf, weekDays } from '../utils/schedule'

interface ShiftValues {
  name: string
  startTime: Dayjs
  endTime: Dayjs
}

/** A row of the grid; people who left since being scheduled have no role and cannot get new shifts. */
interface Row {
  id: number
  fullName: string
  role?: Role
}

const iso = (d: Dayjs) => d.format('YYYY-MM-DD')
const shiftLabel = (s: { name?: string; shiftName?: string; startTime: string; endTime: string }) =>
  `${s.name ?? s.shiftName} ${hhmm(s.startTime)}–${hhmm(s.endTime)}`

/** FR-13.1 → 13.3: shift templates and the weekly schedule (BR-23). */
export default function SchedulePage() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  const [form] = Form.useForm<ShiftValues>()
  const [monday, setMonday] = useState(() => mondayOf(dayjs()))
  const [templatesOpen, setTemplatesOpen] = useState(false)
  const [editingShift, setEditingShift] = useState<WorkShift | null>(null)
  const from = iso(monday)
  const days = weekDays(monday)

  const shifts = useQuery({ queryKey: ['work-shifts'], queryFn: () => api.get<WorkShift[]>('/work-shifts').then((r) => r.data) })
  const staff = useQuery({ queryKey: ['schedule-staff'], queryFn: () => api.get<StaffMember[]>('/schedule/staff').then((r) => r.data) })
  const schedule = useQuery({
    queryKey: ['schedule', from],
    queryFn: () => api.get<ShiftAssignment[]>('/schedule', { params: { from } }).then((r) => r.data),
  })

  const onError = (e: unknown) => message.error(errorMessage(e))
  const refreshSchedule = () => queryClient.invalidateQueries({ queryKey: ['schedule'] })
  const refreshShifts = () => queryClient.invalidateQueries({ queryKey: ['work-shifts'] })

  const assign = useMutation({
    mutationFn: (body: { employeeId: number; workShiftId: number; workDate: string }) => api.post('/schedule', body),
    onSuccess: refreshSchedule,
    onError,
  })
  const unassign = useMutation({ mutationFn: (id: number) => api.delete(`/schedule/${id}`), onSuccess: refreshSchedule, onError })
  const copyWeek = useMutation({
    mutationFn: () =>
      api
        .post<{ copied: number; skipped: number }>('/schedule/copy-week', { fromWeek: iso(monday.subtract(7, 'day')), toWeek: from })
        .then((r) => r.data),
    onSuccess: (r) => {
      message.success(`Đã chép ${r.copied} ca${r.skipped ? `, bỏ qua ${r.skipped} ca bị trùng hoặc của người đã nghỉ` : ''}`)
      refreshSchedule()
    },
    onError,
  })
  const saveShift = useMutation({
    mutationFn: (values: ShiftValues) => {
      const body = { name: values.name, startTime: values.startTime.format('HH:mm'), endTime: values.endTime.format('HH:mm') }
      return editingShift ? api.put(`/work-shifts/${editingShift.id}`, body) : api.post('/work-shifts', body)
    },
    onSuccess: () => {
      message.success('Đã lưu ca mẫu')
      setEditingShift(null)
      form.resetFields()
      refreshShifts()
      refreshSchedule()
    },
    onError,
  })
  const toggleShift = useMutation({
    mutationFn: (s: WorkShift) => api.put(`/work-shifts/${s.id}`, { name: s.name, startTime: hhmm(s.startTime), endTime: hhmm(s.endTime), active: s.active }),
    onSuccess: refreshShifts,
    onError,
  })

  const cells = new Map<string, ShiftAssignment[]>()
  for (const a of schedule.data ?? []) {
    const key = `${a.employeeId}|${a.workDate}`
    cells.set(key, [...(cells.get(key) ?? []), a])
  }
  const rows: Row[] = [...(staff.data ?? [])]
  for (const a of schedule.data ?? []) {
    if (!rows.some((r) => r.id === a.employeeId)) rows.push({ id: a.employeeId, fullName: a.employeeName })
  }
  const shiftOptions = (shifts.data ?? []).filter((s) => s.active).map((s) => ({ label: shiftLabel(s), value: s.id }))

  const editShift = (s: WorkShift) => {
    setEditingShift(s)
    form.setFieldsValue({ name: s.name, startTime: timeOf(s.startTime), endTime: timeOf(s.endTime) })
  }

  return (
    <>
      <div className="page-title">
        <Typography.Title level={3}>Xếp ca</Typography.Title>
        <Space wrap>
          <Button icon={<LeftOutlined />} onClick={() => setMonday(monday.subtract(7, 'day'))} title="Tuần trước" />
          <DatePicker
            value={monday}
            allowClear={false}
            format={(v) => `${v.format('DD/MM')} – ${v.add(6, 'day').format('DD/MM/YYYY')}`}
            onChange={(d) => d && setMonday(mondayOf(d))}
          />
          <Button icon={<RightOutlined />} onClick={() => setMonday(monday.add(7, 'day'))} title="Tuần sau" />
          <Popconfirm
            title="Chép lịch tuần trước sang tuần này?"
            description="Bỏ qua ca bị trùng giờ và người đã nghỉ việc."
            onConfirm={() => copyWeek.mutate()}
          >
            <Button icon={<CopyOutlined />} loading={copyWeek.isPending}>Chép tuần trước</Button>
          </Popconfirm>
          <Button icon={<SettingOutlined />} onClick={() => setTemplatesOpen(true)}>Ca mẫu</Button>
        </Space>
      </div>

      <Table<Row>
        size="small"
        rowKey="id"
        bordered
        pagination={false}
        loading={staff.isLoading || schedule.isLoading}
        dataSource={rows}
        scroll={{ x: 1150 }}
        columns={[
          {
            title: 'Nhân viên',
            fixed: 'left',
            width: 170,
            render: (_, p) => (
              <>
                <div>{p.fullName}</div>
                <Typography.Text type="secondary">{p.role ? roleLabel[p.role] : 'Đã nghỉ việc'}</Typography.Text>
              </>
            ),
          },
          ...days.map((d) => ({
            key: iso(d),
            title: d.format('dd DD/MM'),
            width: 140,
            render: (_: unknown, p: Row) => {
              const day = iso(d)
              return (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 4, alignItems: 'flex-start' }}>
                  {(cells.get(`${p.id}|${day}`) ?? []).map((a) => (
                    <Tag
                      key={a.id}
                      color="blue"
                      closable
                      onClose={(e) => {
                        e.preventDefault()
                        unassign.mutate(a.id)
                      }}
                    >
                      {shiftLabel(a)}
                    </Tag>
                  ))}
                  {p.role && (
                    <Select<number>
                      size="small"
                      placeholder="+ Xếp ca"
                      value={null}
                      options={shiftOptions}
                      popupMatchSelectWidth={false}
                      style={{ width: 110 }}
                      onChange={(workShiftId) => assign.mutate({ employeeId: p.id, workShiftId, workDate: day })}
                    />
                  )}
                </div>
              )
            },
          })),
        ]}
      />

      <Drawer title="Ca mẫu" open={templatesOpen} onClose={() => setTemplatesOpen(false)} size={520}>
        <Table<WorkShift>
          size="small"
          rowKey="id"
          pagination={false}
          dataSource={shifts.data ?? []}
          columns={[
            { title: 'Tên', dataIndex: 'name' },
            { title: 'Giờ', render: (_, s) => `${hhmm(s.startTime)}–${hhmm(s.endTime)}` },
            {
              title: 'Đang dùng',
              render: (_, s) => <Switch size="small" checked={s.active} onChange={(active) => toggleShift.mutate({ ...s, active })} />,
            },
            { title: '', render: (_, s) => <Button size="small" onClick={() => editShift(s)}>Sửa</Button> },
          ]}
        />
        <Typography.Title level={5} style={{ marginTop: 16 }}>
          {editingShift ? `Sửa ca ${editingShift.name}` : 'Thêm ca mẫu'}
        </Typography.Title>
        <Form form={form} layout="vertical" onFinish={(v) => saveShift.mutate(v)}>
          <Form.Item name="name" label="Tên ca" rules={[{ required: true, max: 50 }]}>
            <Input placeholder="Sáng, Chiều, Tối..." />
          </Form.Item>
          <Space>
            <Form.Item name="startTime" label="Bắt đầu" rules={[{ required: true }]}>
              <TimePicker format="HH:mm" minuteStep={15} />
            </Form.Item>
            <Form.Item name="endTime" label="Kết thúc" rules={[{ required: true }]}>
              <TimePicker format="HH:mm" minuteStep={15} />
            </Form.Item>
          </Space>
          <Typography.Paragraph type="secondary">
            Ca không được qua nửa đêm. Ca đã xếp cho nhân viên thì không đổi giờ được, hãy tạo ca mới.
          </Typography.Paragraph>
          <Space>
            <Button type="primary" htmlType="submit" loading={saveShift.isPending}>Lưu</Button>
            {editingShift && (
              <Button
                onClick={() => {
                  setEditingShift(null)
                  form.resetFields()
                }}
              >
                Huỷ sửa
              </Button>
            )}
          </Space>
        </Form>
      </Drawer>
    </>
  )
}
