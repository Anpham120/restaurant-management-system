import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Button, Drawer, Flex, Form, Input, InputNumber, Modal, Space, Switch, Table, Tabs, Tag, Typography } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { api, errorMessage } from '@/shared/api/client'
import type { InventoryItem, MovementType, StockMovement } from '@/shared/api/types'
import { money, movementLabel, time } from '@/shared/utils/format'
import ReceiptsTab from '../components/ReceiptsTab'
import SuppliersTab from '../components/SuppliersTab'
import { stockValue } from '../utils/receipt'

/** FR-09: ingredients, stock movements and low-stock warnings; receipts with prices and suppliers (FR-09.5 → FR-09.7). */
export default function InventoryPage() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  const [lowOnly, setLowOnly] = useState(false)
  const [editing, setEditing] = useState<{ record: InventoryItem | null } | null>(null)
  const [moving, setMoving] = useState<{ item: InventoryItem; type: MovementType } | null>(null)
  const [historyOf, setHistoryOf] = useState<InventoryItem | null>(null)

  const items = useQuery({ queryKey: ['inventory'], queryFn: () => api.get<InventoryItem[]>('/inventory-items').then((r) => r.data) })
  const history = useQuery({
    queryKey: ['inventory-movements', historyOf?.id],
    queryFn: () => api.get<StockMovement[]>(`/inventory-items/${historyOf!.id}/movements`).then((r) => r.data),
    enabled: historyOf !== null,
  })

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['inventory'] })
  const onError = (e: unknown) => message.error(errorMessage(e))

  const save = useMutation({
    mutationFn: (values: Partial<InventoryItem>) =>
      editing?.record ? api.put(`/inventory-items/${editing.record.id}`, values) : api.post('/inventory-items', values),
    onSuccess: () => {
      setEditing(null)
      refresh()
    },
    onError,
  })
  const move = useMutation({
    mutationFn: (values: { quantity: number; note?: string }) =>
      api.post(`/inventory-items/${moving!.item.id}/movements`, { type: moving!.type, ...values }),
    onSuccess: () => {
      message.success('Đã ghi phiếu')
      setMoving(null)
      refresh()
    },
    onError,
  })

  const data = (items.data ?? []).filter((i) => !lowOnly || i.lowStock)
  const totalValue = (items.data ?? []).reduce((sum, i) => sum + (stockValue(i) ?? 0), 0)

  return (
    <>
      <div className="page-title">
        <Typography.Title level={3}>Kho nguyên liệu</Typography.Title>
      </div>
      <Tabs
        items={[
          {
            key: 'items',
            label: 'Nguyên liệu',
            children: (
              <>
                <Flex justify="space-between" align="center" gap={8} wrap style={{ marginBottom: 16 }}>
                  <Typography.Text strong>Giá trị tồn: {money(totalValue)}</Typography.Text>
                  <Space>
                    <Switch checked={lowOnly} onChange={setLowOnly} /> Chỉ hiện sắp hết
                    <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing({ record: null })}>
                      Thêm nguyên liệu
                    </Button>
                  </Space>
                </Flex>
                <Table<InventoryItem>
                  size="small"
                  rowKey="id"
                  loading={items.isLoading}
                  dataSource={data}
                  scroll={{ x: 960 }}
                  columns={[
                    { title: 'Nguyên liệu', dataIndex: 'name' },
                    { title: 'Tồn', render: (_, i) => `${i.quantity} ${i.unit}` },
                    { title: 'Tối thiểu', render: (_, i) => `${i.minQuantity} ${i.unit}` },
                    { title: 'Giá vốn', render: (_, i) => (i.unitCost === null ? '' : `${money(i.unitCost)}/${i.unit}`) },
                    {
                      title: 'Giá trị tồn',
                      render: (_, i) => {
                        const value = stockValue(i)
                        return value === null ? '' : money(value)
                      },
                    },
                    { title: '', render: (_, i) => (i.lowStock ? <Tag color="red">Sắp hết</Tag> : <Tag color="green">Đủ</Tag>) },
                    {
                      title: '',
                      render: (_, i) => (
                        <Space wrap>
                          <Button size="small" onClick={() => setMoving({ item: i, type: 'IN' })}>Nhập</Button>
                          <Button size="small" onClick={() => setMoving({ item: i, type: 'OUT' })}>Xuất</Button>
                          <Button size="small" onClick={() => setMoving({ item: i, type: 'ADJUST' })}>Kiểm kê</Button>
                          <Button size="small" onClick={() => setHistoryOf(i)}>Lịch sử</Button>
                          <Button size="small" onClick={() => setEditing({ record: i })}>Sửa</Button>
                        </Space>
                      ),
                    },
                  ]}
                />
              </>
            ),
          },
          { key: 'receipts', label: 'Phiếu nhập', children: <ReceiptsTab items={items.data ?? []} /> },
          { key: 'suppliers', label: 'Nhà cung cấp', children: <SuppliersTab /> },
        ]}
      />

      <Modal title={editing?.record ? 'Sửa nguyên liệu' : 'Thêm nguyên liệu'} open={editing !== null} onCancel={() => setEditing(null)} footer={null} destroyOnHidden>
        <Form layout="vertical" initialValues={editing?.record ?? { minQuantity: 0 }} onFinish={(v) => save.mutate(v)}>
          <Form.Item name="name" label="Tên" rules={[{ required: true, max: 100 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="unit" label="Đơn vị" rules={[{ required: true, max: 20 }]}>
            <Input placeholder="kg, lít, chai..." />
          </Form.Item>
          <Form.Item name="minQuantity" label="Mức tối thiểu (cảnh báo khi tồn ≤ mức này)" rules={[{ required: true }]}>
            <InputNumber min={0} step={0.5} />
          </Form.Item>
          {!editing?.record && <Typography.Paragraph type="secondary">Tồn ban đầu là 0. Dùng "Nhập" để ghi số lượng.</Typography.Paragraph>}
          <Button type="primary" htmlType="submit" block loading={save.isPending}>Lưu</Button>
        </Form>
      </Modal>

      <Modal
        title={moving ? `${movementLabel[moving.type]}: ${moving.item.name}` : ''}
        open={moving !== null}
        onCancel={() => setMoving(null)}
        footer={null}
        destroyOnHidden
      >
        {moving && (
          <Form layout="vertical" onFinish={(v) => move.mutate(v)}>
            <Typography.Paragraph>
              Tồn hiện tại: {moving.item.quantity} {moving.item.unit}
            </Typography.Paragraph>
            {moving.type === 'IN' && (
              <Typography.Paragraph type="secondary">
                Nhập tay không có giá nên giá vốn giữ nguyên. Hàng mua có giá thì lập ở thẻ "Phiếu nhập".
              </Typography.Paragraph>
            )}
            <Form.Item
              name="quantity"
              label={moving.type === 'ADJUST' ? `Số đếm thực tế (${moving.item.unit})` : `Số lượng (${moving.item.unit})`}
              rules={[{ required: true }]}
            >
              <InputNumber min={0} step={0.5} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="note" label="Ghi chú" rules={[{ max: 300 }]}>
              <Input placeholder="Nhà cung cấp, lý do hỏng..." />
            </Form.Item>
            <Button type="primary" htmlType="submit" block loading={move.isPending}>Ghi phiếu</Button>
          </Form>
        )}
      </Modal>

      <Drawer title={`Lịch sử: ${historyOf?.name ?? ''}`} open={historyOf !== null} onClose={() => setHistoryOf(null)} size={560}>
        <Table<StockMovement>
          size="small"
          rowKey="id"
          loading={history.isLoading}
          dataSource={history.data ?? []}
          pagination={{ pageSize: 20 }}
          columns={[
            { title: 'Thời gian', render: (_, m) => time(m.createdAt) },
            { title: 'Loại', render: (_, m) => movementLabel[m.type] },
            { title: 'Thay đổi', render: (_, m) => (m.quantityChange > 0 ? `+${m.quantityChange}` : m.quantityChange) },
            { title: 'Còn', dataIndex: 'quantityAfter' },
            { title: 'Người lập', dataIndex: 'createdByName' },
            { title: 'Ghi chú', dataIndex: 'note' },
          ]}
        />
      </Drawer>
    </>
  )
}
