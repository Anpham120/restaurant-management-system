import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Button, Card, Form, Input, InputNumber, Spin, Typography } from 'antd'
import { api, errorMessage } from '@/shared/api/client'
import type { Settings } from '@/shared/api/types'

/** FR-11: restaurant details, the bank account used for VietQR, and when a kitchen dish is late. */
export default function SettingsPage() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  const settings = useQuery({ queryKey: ['settings'], queryFn: () => api.get<Settings>('/settings').then((r) => r.data) })

  const save = useMutation({
    mutationFn: (values: Settings) => api.put<Settings>('/settings', values).then((r) => r.data),
    onSuccess: (data) => {
      queryClient.setQueryData(['settings'], data)
      message.success('Đã lưu cài đặt')
    },
    onError: (e) => message.error(errorMessage(e)),
  })

  if (settings.isLoading) return <Spin />

  return (
    <>
      <div className="page-title">
        <Typography.Title level={3}>Cài đặt</Typography.Title>
      </div>
      <Card style={{ maxWidth: 560 }}>
        <Form layout="vertical" initialValues={settings.data} onFinish={(v) => save.mutate(v)}>
          <Typography.Title level={5}>Nhà hàng</Typography.Title>
          <Form.Item name="name" label="Tên" rules={[{ required: true, max: 150 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="address" label="Địa chỉ" rules={[{ max: 300 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="phone" label="Điện thoại" rules={[{ max: 20 }]}>
            <Input />
          </Form.Item>
          <Typography.Title level={5}>Tài khoản nhận chuyển khoản (VietQR)</Typography.Title>
          <Form.Item
            name="bankCode"
            label="Mã ngân hàng"
            extra="Mã BIN hoặc tên viết tắt theo VietQR, ví dụ 970436 (Vietcombank), 970422 (MB), 970415 (VietinBank)"
            rules={[{ max: 20 }]}
          >
            <Input />
          </Form.Item>
          <Form.Item name="bankAccountNo" label="Số tài khoản" rules={[{ max: 30 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="bankAccountName" label="Tên chủ tài khoản" rules={[{ max: 100 }]}>
            <Input placeholder="VIẾT HOA KHÔNG DẤU" />
          </Form.Item>
          <Typography.Title level={5}>Bếp</Typography.Title>
          <Form.Item
            name="waitAlertMinutes"
            label="Tô đỏ món chờ từ (phút)"
            extra="Tính từ lúc món vào bếp, từ 1 đến 120 phút"
            rules={[{ required: true }]}
          >
            <InputNumber min={1} max={120} precision={0} />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={save.isPending}>
            Lưu
          </Button>
        </Form>
      </Card>
    </>
  )
}
