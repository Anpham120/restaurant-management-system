import { useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Button, Flex, Form, Input, InputNumber, Modal, Popconfirm, QRCode, Space, Table, Tag, Typography } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { api, errorMessage } from '../../api/client'
import type { DiningTable, Settings } from '../../api/types'

/** Prints one table card: restaurant name, table name and the QR image (FR-04.2). */
function printCard(restaurant: string, table: DiningTable, canvas: HTMLCanvasElement) {
  const win = window.open('', '_blank', 'width=420,height=600')
  if (!win) return
  const image = canvas.toDataURL('image/png')
  win.document.write(`<!doctype html><html><head><meta charset="utf-8"><title>QR ${table.name}</title>
    <style>body{font-family:sans-serif;text-align:center;padding:24px}h1{margin:0}h2{margin:4px 0 16px;color:#b45309}</style>
    </head><body><h2>${restaurant}</h2><h1>Bàn ${table.name}</h1>
    <img src="${image}" width="280" height="280" alt="QR"><p>Quét mã để xem thực đơn và gọi món</p></body></html>`)
  win.document.close()
  win.onload = () => win.print()
}

/** FR-04: tables and their QR codes. */
export default function TablesAdminPage() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  const [editing, setEditing] = useState<{ record: DiningTable | null } | null>(null)
  const [qrTable, setQrTable] = useState<DiningTable | null>(null)
  const qrRef = useRef<HTMLDivElement>(null)

  const tables = useQuery({ queryKey: ['tables'], queryFn: () => api.get<DiningTable[]>('/tables').then((r) => r.data) })
  const settings = useQuery({ queryKey: ['settings'], queryFn: () => api.get<Settings>('/settings').then((r) => r.data) })

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['tables'] })
  const onError = (e: unknown) => message.error(errorMessage(e))

  const save = useMutation({
    mutationFn: (values: Partial<DiningTable>) =>
      editing?.record ? api.put(`/tables/${editing.record.id}`, values) : api.post('/tables', values),
    onSuccess: () => {
      setEditing(null)
      message.success('Đã lưu bàn')
      refresh()
    },
    onError,
  })
  const remove = useMutation({ mutationFn: (id: number) => api.delete(`/tables/${id}`), onSuccess: refresh, onError })
  const regenerate = useMutation({
    mutationFn: (id: number) => api.post<DiningTable>(`/tables/${id}/qr-token`).then((r) => r.data),
    onSuccess: (table) => {
      message.success(`Đã tạo mã mới cho bàn ${table.name}. Nhớ in lại thẻ.`)
      setQrTable((current) => (current && current.id === table.id ? table : current))
      refresh()
    },
    onError,
  })

  return (
    <>
      <div className="page-title">
        <Typography.Title level={3}>Bàn và mã QR</Typography.Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing({ record: null })}>
          Thêm bàn
        </Button>
      </div>
      <Table<DiningTable>
        size="small"
        rowKey="id"
        loading={tables.isLoading}
        dataSource={tables.data ?? []}
        columns={[
          { title: 'Bàn', dataIndex: 'name' },
          { title: 'Khu', dataIndex: 'area' },
          { title: 'Số ghế', dataIndex: 'seats' },
          {
            title: 'Trạng thái',
            render: (_, t) => <Tag color={t.status === 'OCCUPIED' ? 'orange' : 'green'}>{t.status === 'OCCUPIED' ? 'Có khách' : 'Trống'}</Tag>,
          },
          {
            title: '',
            render: (_, t) => (
              <Space wrap>
                <Button size="small" onClick={() => setQrTable(t)}>Xem QR</Button>
                <Popconfirm
                  title="Tạo mã QR mới?"
                  description="Thẻ cũ sẽ không dùng được nữa."
                  onConfirm={() => regenerate.mutate(t.id)}
                >
                  <Button size="small">Tạo lại QR</Button>
                </Popconfirm>
                <Button size="small" onClick={() => setEditing({ record: t })}>Sửa</Button>
                <Popconfirm title="Xoá bàn?" onConfirm={() => remove.mutate(t.id)}>
                  <Button size="small" danger>Xoá</Button>
                </Popconfirm>
              </Space>
            ),
          },
        ]}
      />

      <Modal title={editing?.record ? 'Sửa bàn' : 'Thêm bàn'} open={editing !== null} onCancel={() => setEditing(null)} footer={null} destroyOnHidden>
        <Form layout="vertical" initialValues={editing?.record ?? { seats: 4 }} onFinish={(v) => save.mutate(v)}>
          <Form.Item name="name" label="Tên bàn" rules={[{ required: true, max: 50 }]}>
            <Input placeholder="Ví dụ B09" />
          </Form.Item>
          <Form.Item name="area" label="Khu vực" rules={[{ max: 50 }]}>
            <Input placeholder="Tầng 1, Sân trong..." />
          </Form.Item>
          <Form.Item name="seats" label="Số ghế" rules={[{ required: true }]}>
            <InputNumber min={1} max={50} />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={save.isPending}>Lưu</Button>
        </Form>
      </Modal>

      <Modal title={`Mã QR bàn ${qrTable?.name ?? ''}`} open={qrTable !== null} onCancel={() => setQrTable(null)} footer={null} destroyOnHidden>
        {qrTable && (
          <Flex vertical align="center" gap={8}>
            <div ref={qrRef}>
              <QRCode value={qrTable.qrUrl} size={240} type="canvas" />
            </div>
            <Typography.Link href={qrTable.qrUrl} target="_blank" copyable>
              {qrTable.qrUrl}
            </Typography.Link>
            <Button
              type="primary"
              onClick={() => {
                const canvas = qrRef.current?.querySelector('canvas')
                if (canvas) printCard(settings.data?.name ?? '', qrTable, canvas)
              }}
            >
              In thẻ QR
            </Button>
          </Flex>
        )}
      </Modal>
    </>
  )
}
