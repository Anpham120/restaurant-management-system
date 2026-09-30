import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Button, DatePicker, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, Tag, Typography } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import dayjs, { type Dayjs } from 'dayjs'
import { api, errorMessage } from '../../api/client'
import type { EmployeeDetail, PayType, Role } from '../../api/types'
import { moneyInputProps, payText, payTypeLabel, roleLabel } from '../../utils/format'

const ROLE_OPTIONS = (Object.keys(roleLabel) as Role[]).map((r) => ({ label: roleLabel[r], value: r }))
const PAY_OPTIONS = (Object.keys(payTypeLabel) as PayType[]).map((p) => ({ label: payTypeLabel[p], value: p }))

interface ProfileValues {
  phone?: string
  hiredOn?: Dayjs | null
  payType: PayType
  payRate: number
}

const isoDate = (value?: Dayjs | null) => (value ? value.format('YYYY-MM-DD') : null)
const viDate = (value: string | null) => (value ? dayjs(value).format('DD/MM/YYYY') : '')

/** FR-12: phone, start date and pay. */
function ProfileFields() {
  return (
    <>
      <Form.Item name="phone" label="Số điện thoại" rules={[{ max: 20, pattern: /^[0-9+ .()-]*$/, message: 'Chỉ gồm số và các dấu + . ( ) -' }]}>
        <Input />
      </Form.Item>
      <Form.Item name="hiredOn" label="Ngày vào làm">
        <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
      </Form.Item>
      <Form.Item name="payType" label="Hình thức lương" rules={[{ required: true }]}>
        <Select options={PAY_OPTIONS} />
      </Form.Item>
      <Form.Item name="payRate" label="Mức lương (đồng mỗi giờ hoặc mỗi tháng)" rules={[{ required: true }]}>
        <InputNumber min={0} step={1000} style={{ width: '100%' }} {...moneyInputProps} />
      </Form.Item>
    </>
  )
}

/** FR-02, FR-12: accounts are locked, never deleted (BR-03); pay is for ADMIN only (BR-22). */
export default function EmployeesPage() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  const [editing, setEditing] = useState<{ record: EmployeeDetail | null } | null>(null)
  const [resetting, setResetting] = useState<EmployeeDetail | null>(null)
  const [profileOf, setProfileOf] = useState<EmployeeDetail | null>(null)
  const [resigning, setResigning] = useState<EmployeeDetail | null>(null)

  const employees = useQuery({ queryKey: ['employees'], queryFn: () => api.get<EmployeeDetail[]>('/employees').then((r) => r.data) })

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['employees'] })
  const onError = (e: unknown) => message.error(errorMessage(e))

  const save = useMutation({
    mutationFn: async (values: Record<string, unknown> & Partial<ProfileValues>) => {
      if (editing?.record) {
        await api.put(`/employees/${editing.record.id}`, { fullName: values.fullName, role: values.role })
      } else {
        await api.post('/employees', { ...values, hiredOn: isoDate(values.hiredOn) })
      }
    },
    onSuccess: () => {
      setEditing(null)
      message.success('Đã lưu')
      refresh()
    },
    onError,
  })
  const saveProfile = useMutation({
    mutationFn: (values: ProfileValues) => api.put(`/employees/${profileOf!.id}/profile`, { ...values, hiredOn: isoDate(values.hiredOn) }),
    onSuccess: () => {
      setProfileOf(null)
      message.success('Đã lưu hồ sơ')
      refresh()
    },
    onError,
  })
  const resign = useMutation({
    mutationFn: (leftOn: Dayjs) => api.post(`/employees/${resigning!.id}/resign`, { leftOn: isoDate(leftOn) }),
    onSuccess: () => {
      setResigning(null)
      message.success('Đã ghi nghỉ việc và khoá tài khoản')
      refresh()
    },
    onError,
  })
  const setActive = useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) => api.patch(`/employees/${id}/active`, { active }),
    onSuccess: refresh,
    onError,
  })
  const resetPassword = useMutation({
    mutationFn: (newPassword: string) => api.post(`/employees/${resetting!.id}/reset-password`, { newPassword }),
    onSuccess: () => {
      message.success('Đã đặt lại mật khẩu')
      setResetting(null)
    },
    onError,
  })

  return (
    <>
      <div className="page-title">
        <Typography.Title level={3}>Nhân viên</Typography.Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing({ record: null })}>
          Thêm nhân viên
        </Button>
      </div>
      <Table<EmployeeDetail>
        size="small"
        rowKey="id"
        loading={employees.isLoading}
        dataSource={employees.data ?? []}
        scroll={{ x: true }}
        columns={[
          { title: 'Họ tên', dataIndex: 'fullName' },
          { title: 'Tên đăng nhập', dataIndex: 'username' },
          { title: 'Vai trò', render: (_, e) => <Tag>{roleLabel[e.role]}</Tag> },
          { title: 'Điện thoại', dataIndex: 'phone' },
          { title: 'Vào làm', render: (_, e) => viDate(e.hiredOn) },
          { title: 'Lương', render: (_, e) => payText(e.payType, e.payRate) },
          {
            title: 'Trạng thái',
            render: (_, e) => {
              if (e.leftOn) return <Tag color="red">Nghỉ việc từ {viDate(e.leftOn)}</Tag>
              return e.active ? <Tag color="green">Hoạt động</Tag> : <Tag color="red">Đã khoá</Tag>
            },
          },
          {
            title: '',
            render: (_, e) => (
              <Space wrap>
                <Button size="small" onClick={() => setEditing({ record: e })}>Sửa</Button>
                <Button size="small" onClick={() => setProfileOf(e)}>Hồ sơ, lương</Button>
                <Button size="small" onClick={() => setResetting(e)}>Đặt lại mật khẩu</Button>
                <Popconfirm
                  title={e.active ? 'Khoá tài khoản này?' : 'Mở khoá tài khoản này?'}
                  description={e.active ? 'Người này sẽ bị đăng xuất ngay.' : e.leftOn ? 'Người này sẽ được tính là đi làm lại.' : undefined}
                  onConfirm={() => setActive.mutate({ id: e.id, active: !e.active })}
                >
                  <Button size="small" danger={e.active}>{e.active ? 'Khoá' : 'Mở khoá'}</Button>
                </Popconfirm>
                {!e.leftOn && (
                  <Button size="small" danger onClick={() => setResigning(e)}>Cho nghỉ việc</Button>
                )}
              </Space>
            ),
          },
        ]}
      />

      <Modal title={editing?.record ? 'Sửa nhân viên' : 'Thêm nhân viên'} open={editing !== null} onCancel={() => setEditing(null)} footer={null} destroyOnHidden>
        <Form layout="vertical" initialValues={editing?.record ?? { role: 'WAITER', payType: 'HOURLY', payRate: 0 }} onFinish={(v) => save.mutate(v)}>
          <Form.Item name="fullName" label="Họ tên" rules={[{ required: true, max: 100 }]}>
            <Input />
          </Form.Item>
          <Form.Item
            name="username"
            label="Tên đăng nhập"
            rules={[{ required: true, min: 3, max: 50, pattern: /^[a-zA-Z0-9._-]+$/, message: 'Chữ không dấu, số, dấu chấm, gạch' }]}
          >
            <Input disabled={Boolean(editing?.record)} />
          </Form.Item>
          <Form.Item name="role" label="Vai trò" rules={[{ required: true }]}>
            <Select options={ROLE_OPTIONS} />
          </Form.Item>
          {!editing?.record && (
            <>
              <Form.Item name="password" label="Mật khẩu" rules={[{ required: true, min: 6, message: 'Ít nhất 6 ký tự' }]}>
                <Input.Password />
              </Form.Item>
              <ProfileFields />
            </>
          )}
          <Button type="primary" htmlType="submit" block loading={save.isPending}>Lưu</Button>
        </Form>
      </Modal>

      <Modal title={`Hồ sơ và lương: ${profileOf?.fullName ?? ''}`} open={profileOf !== null} onCancel={() => setProfileOf(null)} footer={null} destroyOnHidden>
        {profileOf && (
          <Form
            layout="vertical"
            initialValues={{ ...profileOf, hiredOn: profileOf.hiredOn ? dayjs(profileOf.hiredOn) : null }}
            onFinish={(v: ProfileValues) => saveProfile.mutate(v)}
          >
            <ProfileFields />
            <Typography.Paragraph type="secondary">
              Đổi mức lương không làm đổi phiếu lương đã tính. Muốn áp dụng cho tháng đang làm thì tính lại bảng lương.
            </Typography.Paragraph>
            <Button type="primary" htmlType="submit" block loading={saveProfile.isPending}>Lưu</Button>
          </Form>
        )}
      </Modal>

      <Modal title={`Cho nghỉ việc: ${resigning?.fullName ?? ''}`} open={resigning !== null} onCancel={() => setResigning(null)} footer={null} destroyOnHidden>
        <Form layout="vertical" initialValues={{ leftOn: dayjs() }} onFinish={(v: { leftOn: Dayjs }) => resign.mutate(v.leftOn)}>
          <Form.Item name="leftOn" label="Ngày nghỉ việc" rules={[{ required: true }]}>
            <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
          </Form.Item>
          <Typography.Paragraph type="secondary">
            Tài khoản bị khoá ngay. Hồ sơ, chấm công và phiếu lương cũ vẫn được giữ.
          </Typography.Paragraph>
          <Button type="primary" danger htmlType="submit" block loading={resign.isPending}>Xác nhận</Button>
        </Form>
      </Modal>

      <Modal title={`Đặt lại mật khẩu: ${resetting?.fullName ?? ''}`} open={resetting !== null} onCancel={() => setResetting(null)} footer={null} destroyOnHidden>
        <Form layout="vertical" onFinish={(v: { newPassword: string }) => resetPassword.mutate(v.newPassword)}>
          <Form.Item name="newPassword" label="Mật khẩu mới" rules={[{ required: true, min: 6, message: 'Ít nhất 6 ký tự' }]}>
            <Input.Password />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={resetPassword.isPending}>Lưu</Button>
        </Form>
      </Modal>
    </>
  )
}
