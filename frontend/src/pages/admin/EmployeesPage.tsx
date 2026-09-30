import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Button, Form, Input, Modal, Popconfirm, Select, Space, Table, Tag, Typography } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { api, errorMessage } from '../../api/client'
import type { Employee, Role } from '../../api/types'
import { roleLabel } from '../../utils/format'

const ROLE_OPTIONS = (Object.keys(roleLabel) as Role[]).map((r) => ({ label: roleLabel[r], value: r }))

/** FR-02: accounts are locked, never deleted (BR-03). */
export default function EmployeesPage() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  const [editing, setEditing] = useState<{ record: Employee | null } | null>(null)
  const [resetting, setResetting] = useState<Employee | null>(null)

  const employees = useQuery({ queryKey: ['employees'], queryFn: () => api.get<Employee[]>('/employees').then((r) => r.data) })

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['employees'] })
  const onError = (e: unknown) => message.error(errorMessage(e))

  const save = useMutation({
    mutationFn: async (values: Record<string, unknown>) => {
      if (editing?.record) {
        await api.put(`/employees/${editing.record.id}`, { fullName: values.fullName, role: values.role })
      } else {
        await api.post('/employees', values)
      }
    },
    onSuccess: () => {
      setEditing(null)
      message.success('Đã lưu')
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
      <Table<Employee>
        size="small"
        rowKey="id"
        loading={employees.isLoading}
        dataSource={employees.data ?? []}
        columns={[
          { title: 'Họ tên', dataIndex: 'fullName' },
          { title: 'Tên đăng nhập', dataIndex: 'username' },
          { title: 'Vai trò', render: (_, e) => <Tag>{roleLabel[e.role]}</Tag> },
          { title: 'Trạng thái', render: (_, e) => (e.active ? <Tag color="green">Hoạt động</Tag> : <Tag color="red">Đã khoá</Tag>) },
          {
            title: '',
            render: (_, e) => (
              <Space wrap>
                <Button size="small" onClick={() => setEditing({ record: e })}>Sửa</Button>
                <Button size="small" onClick={() => setResetting(e)}>Đặt lại mật khẩu</Button>
                <Popconfirm
                  title={e.active ? 'Khoá tài khoản này?' : 'Mở khoá tài khoản này?'}
                  description={e.active ? 'Người này sẽ bị đăng xuất ngay.' : undefined}
                  onConfirm={() => setActive.mutate({ id: e.id, active: !e.active })}
                >
                  <Button size="small" danger={e.active}>{e.active ? 'Khoá' : 'Mở khoá'}</Button>
                </Popconfirm>
              </Space>
            ),
          },
        ]}
      />

      <Modal title={editing?.record ? 'Sửa nhân viên' : 'Thêm nhân viên'} open={editing !== null} onCancel={() => setEditing(null)} footer={null} destroyOnHidden>
        <Form layout="vertical" initialValues={editing?.record ?? { role: 'WAITER' }} onFinish={(v) => save.mutate(v)}>
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
            <Form.Item name="password" label="Mật khẩu" rules={[{ required: true, min: 6, message: 'Ít nhất 6 ký tự' }]}>
              <Input.Password />
            </Form.Item>
          )}
          <Button type="primary" htmlType="submit" block loading={save.isPending}>Lưu</Button>
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
