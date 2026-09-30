import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Button, Card, Col, Form, Input, InputNumber, Modal, Popconfirm, Row, Select, Space, Switch, Table, Typography } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { api, errorMessage } from '../../api/client'
import type { Category, MenuItem } from '../../api/types'
import { money } from '../../utils/format'

type Editing<T> = { record: T | null } | null

/** FR-03: categories and dishes. Ordered dishes can only be marked sold out (BR-18). */
export default function MenuAdminPage() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  const [categoryEdit, setCategoryEdit] = useState<Editing<Category>>(null)
  const [itemEdit, setItemEdit] = useState<Editing<MenuItem>>(null)

  const categories = useQuery({ queryKey: ['categories'], queryFn: () => api.get<Category[]>('/categories').then((r) => r.data) })
  const items = useQuery({ queryKey: ['menu-items'], queryFn: () => api.get<MenuItem[]>('/menu-items').then((r) => r.data) })

  const done = (text: string) => () => {
    message.success(text)
    queryClient.invalidateQueries({ queryKey: ['categories'] })
    queryClient.invalidateQueries({ queryKey: ['menu-items'] })
  }
  const onError = (e: unknown) => message.error(errorMessage(e))

  const saveCategory = useMutation({
    mutationFn: (values: Partial<Category>) =>
      categoryEdit?.record ? api.put(`/categories/${categoryEdit.record.id}`, values) : api.post('/categories', values),
    onSuccess: () => {
      setCategoryEdit(null)
      done('Đã lưu danh mục')()
    },
    onError,
  })
  const deleteCategory = useMutation({ mutationFn: (id: number) => api.delete(`/categories/${id}`), onSuccess: done('Đã xoá'), onError })
  const saveItem = useMutation({
    mutationFn: (values: Partial<MenuItem>) =>
      itemEdit?.record ? api.put(`/menu-items/${itemEdit.record.id}`, values) : api.post('/menu-items', values),
    onSuccess: () => {
      setItemEdit(null)
      done('Đã lưu món')()
    },
    onError,
  })
  const deleteItem = useMutation({ mutationFn: (id: number) => api.delete(`/menu-items/${id}`), onSuccess: done('Đã xoá'), onError })
  const setAvailable = useMutation({
    mutationFn: ({ id, available }: { id: number; available: boolean }) => api.patch(`/menu-items/${id}/availability`, { available }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['menu-items'] }),
    onError,
  })

  return (
    <>
      <div className="page-title">
        <Typography.Title level={3}>Thực đơn</Typography.Title>
      </div>
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={8}>
          <Card
            title="Danh mục"
            size="small"
            extra={<Button icon={<PlusOutlined />} onClick={() => setCategoryEdit({ record: null })}>Thêm</Button>}
          >
            <Table<Category>
              size="small"
              rowKey="id"
              pagination={false}
              dataSource={categories.data ?? []}
              columns={[
                { title: 'Tên', dataIndex: 'name' },
                { title: 'Thứ tự', dataIndex: 'sortOrder', width: 70 },
                {
                  title: '',
                  render: (_, c) => (
                    <Space>
                      <Button size="small" onClick={() => setCategoryEdit({ record: c })}>Sửa</Button>
                      <Popconfirm title="Xoá danh mục?" onConfirm={() => deleteCategory.mutate(c.id)}>
                        <Button size="small" danger>Xoá</Button>
                      </Popconfirm>
                    </Space>
                  ),
                },
              ]}
            />
          </Card>
        </Col>
        <Col xs={24} lg={16}>
          <Card title="Món" size="small" extra={<Button icon={<PlusOutlined />} onClick={() => setItemEdit({ record: null })}>Thêm món</Button>}>
            <Table<MenuItem>
              size="small"
              rowKey="id"
              dataSource={items.data ?? []}
              columns={[
                { title: 'Món', dataIndex: 'name' },
                { title: 'Danh mục', dataIndex: 'categoryName' },
                { title: 'Giá', render: (_, m) => money(m.price) },
                {
                  title: 'Còn bán',
                  render: (_, m) => (
                    <Switch size="small" checked={m.available} onChange={(available) => setAvailable.mutate({ id: m.id, available })} />
                  ),
                },
                {
                  title: '',
                  render: (_, m) => (
                    <Space>
                      <Button size="small" onClick={() => setItemEdit({ record: m })}>Sửa</Button>
                      <Popconfirm title="Xoá món?" onConfirm={() => deleteItem.mutate(m.id)}>
                        <Button size="small" danger>Xoá</Button>
                      </Popconfirm>
                    </Space>
                  ),
                },
              ]}
            />
          </Card>
        </Col>
      </Row>

      <Modal
        title={categoryEdit?.record ? 'Sửa danh mục' : 'Thêm danh mục'}
        open={categoryEdit !== null}
        onCancel={() => setCategoryEdit(null)}
        footer={null}
        destroyOnHidden
      >
        <Form
          layout="vertical"
          initialValues={categoryEdit?.record ?? { sortOrder: (categories.data?.length ?? 0) + 1 }}
          onFinish={(v) => saveCategory.mutate(v)}
        >
          <Form.Item name="name" label="Tên" rules={[{ required: true, max: 100 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="sortOrder" label="Thứ tự hiển thị" rules={[{ required: true }]}>
            <InputNumber min={0} max={1000} />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={saveCategory.isPending}>Lưu</Button>
        </Form>
      </Modal>

      <Modal title={itemEdit?.record ? 'Sửa món' : 'Thêm món'} open={itemEdit !== null} onCancel={() => setItemEdit(null)} footer={null} destroyOnHidden>
        <Form layout="vertical" initialValues={itemEdit?.record ?? { available: true }} onFinish={(v) => saveItem.mutate(v)}>
          <Form.Item name="categoryId" label="Danh mục" rules={[{ required: true }]}>
            <Select options={(categories.data ?? []).map((c) => ({ label: c.name, value: c.id }))} />
          </Form.Item>
          <Form.Item name="name" label="Tên món" rules={[{ required: true, max: 150 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="price" label="Giá (đ, đã gồm VAT)" rules={[{ required: true }]}>
            <InputNumber<number>
              min={0}
              step={1000}
              style={{ width: '100%' }}
              formatter={(v) => `${v ?? ''}`.replace(/\B(?=(\d{3})+(?!\d))/g, '.')}
              parser={(v) => Number((v ?? '').replace(/\./g, ''))}
            />
          </Form.Item>
          <Form.Item name="description" label="Mô tả" rules={[{ max: 500 }]}>
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="available" label="Đang bán" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={saveItem.isPending}>Lưu</Button>
        </Form>
      </Modal>
    </>
  )
}
