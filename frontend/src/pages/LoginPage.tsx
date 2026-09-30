import { useState } from 'react'
import { Navigate, useNavigate } from 'react-router'
import { App, Button, Card, Form, Input, Typography } from 'antd'
import { LockOutlined, UserOutlined } from '@ant-design/icons'
import { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { homePath } from '../utils/format'

export default function LoginPage() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const { message } = App.useApp()
  const [submitting, setSubmitting] = useState(false)

  if (user) return <Navigate to={homePath(user.role)} replace />

  const onFinish = async ({ username, password }: { username: string; password: string }) => {
    setSubmitting(true)
    try {
      const signedIn = await login(username, password)
      navigate(homePath(signedIn.role), { replace: true })
    } catch (e) {
      message.error(errorMessage(e))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="center-page">
      <Card style={{ width: 360, maxWidth: '100%' }}>
        <Typography.Title level={3} style={{ color: '#b45309', marginTop: 0 }}>
          Bếp Nhà &amp; Nướng
        </Typography.Title>
        <Typography.Paragraph type="secondary">Đăng nhập dành cho nhân viên</Typography.Paragraph>
        <Form layout="vertical" onFinish={onFinish} requiredMark={false}>
          <Form.Item name="username" label="Tên đăng nhập" rules={[{ required: true, message: 'Nhập tên đăng nhập' }]}>
            <Input prefix={<UserOutlined />} autoComplete="username" autoFocus />
          </Form.Item>
          <Form.Item name="password" label="Mật khẩu" rules={[{ required: true, message: 'Nhập mật khẩu' }]}>
            <Input.Password prefix={<LockOutlined />} autoComplete="current-password" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block size="large" loading={submitting}>
            Đăng nhập
          </Button>
        </Form>
      </Card>
    </div>
  )
}
