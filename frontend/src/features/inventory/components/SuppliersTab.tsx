import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Button, Flex, Form, Input, Modal, Switch, Table, Tag } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { api, errorMessage } from '@/shared/api/client'
import type { Supplier } from '@/shared/api/types'

/** FR-09.5: suppliers are never deleted; a stopped one is kept for its old receipts (BR-37). */
export default function SuppliersTab() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  const [editing, setEditing] = useState<{ record: Supplier | null } | null>(null)

  const suppliers = useQuery({ queryKey: ['suppliers'], queryFn: () => api.get<Supplier[]>('/suppliers').then((r) => r.data) })

  const save = useMutation({
    mutationFn: (values: Partial<Supplier>) =>
      editing?.record ? api.put(`/suppliers/${editing.record.id}`, values) : api.post('/suppliers', values),
    onSuccess: () => {
      setEditing(null)
      queryClient.invalidateQueries({ queryKey: ['suppliers'] })
    },
    onError: (e) => message.error(errorMessage(e)),
  })

  return (
    <>
      <Flex justify="flex-end" style={{ marginBottom: 16 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing({ record: null })}>
          Thêm nhà cung cấp
        </Button>
      </Flex>
      <Table<Supplier>
        size="small"
        rowKey="id"
        loading={suppliers.isLoading}
        dataSource={suppliers.data ?? []}
        scroll={{ x: 800 }}
        columns={[
          { title: 'Nhà cung cấp', dataIndex: 'name' },
          { title: 'Điện thoại', dataIndex: 'phone' },
          { title: 'Địa chỉ', dataIndex: 'address' },
          { title: 'Mã số thuế', dataIndex: 'taxCode' },
          { title: 'Ghi chú', dataIndex: 'note' },
          { title: '', render: (_, s) => (s.active ? <Tag color="green">Đang giao dịch</Tag> : <Tag>Ngừng giao dịch</Tag>) },
          { title: '', render: (_, s) => <Button size="small" onClick={() => setEditing({ record: s })}>Sửa</Button> },
        ]}
      />

      <Modal
        title={editing?.record ? 'Sửa nhà cung cấp' : 'Thêm nhà cung cấp'}
        open={editing !== null}
        onCancel={() => setEditing(null)}
        footer={null}
        destroyOnHidden
      >
        <Form layout="vertical" initialValues={editing?.record ?? undefined} onFinish={(v) => save.mutate(v)}>
          <Form.Item name="name" label="Tên" rules={[{ required: true, whitespace: true, max: 150 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="phone" label="Điện thoại" rules={[{ max: 20 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="address" label="Địa chỉ" rules={[{ max: 300 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="taxCode" label="Mã số thuế" rules={[{ max: 20 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="note" label="Ghi chú" rules={[{ max: 300 }]}>
            <Input placeholder="Mặt hàng, giờ giao, người liên hệ..." />
          </Form.Item>
          {editing?.record && (
            <Form.Item name="active" label="Đang giao dịch" valuePropName="checked" extra="Ngừng giao dịch thì không chọn được khi lập phiếu nhập; phiếu cũ vẫn giữ.">
              <Switch />
            </Form.Item>
          )}
          <Button type="primary" htmlType="submit" block loading={save.isPending}>Lưu</Button>
        </Form>
      </Modal>
    </>
  )
}
