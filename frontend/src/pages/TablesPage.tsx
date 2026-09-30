import { useState } from 'react'
import { useNavigate } from 'react-router'
import { useMutation, useQuery } from '@tanstack/react-query'
import { App, Button, Card, Col, Empty, Flex, InputNumber, Modal, Row, Spin, Tag, Typography } from 'antd'
import { ShoppingOutlined } from '@ant-design/icons'
import { api, errorMessage } from '../api/client'
import type { DiningTable, Order } from '../api/types'
import { money } from '../utils/format'

/** FR-04.4, FR-05.1: floor plan with live state; tap a free table to open it. */
export default function TablesPage() {
  const navigate = useNavigate()
  const { message } = App.useApp()
  const [opening, setOpening] = useState<DiningTable | null>(null)
  const [guestCount, setGuestCount] = useState<number>(2)

  const tables = useQuery({ queryKey: ['tables'], queryFn: () => api.get<DiningTable[]>('/tables').then((r) => r.data) })
  const orders = useQuery({ queryKey: ['orders', 'OPEN'], queryFn: () => api.get<Order[]>('/orders').then((r) => r.data) })

  const createOrder = useMutation({
    mutationFn: (body: object) => api.post<Order>('/orders', body).then((r) => r.data),
    onSuccess: (order) => navigate(`/orders/${order.id}`),
    onError: (e) => message.error(errorMessage(e)),
  })

  if (tables.isLoading) return <Spin />

  const byArea = new Map<string, DiningTable[]>()
  for (const t of tables.data ?? []) {
    const area = t.area ?? 'Khác'
    byArea.set(area, [...(byArea.get(area) ?? []), t])
  }
  const takeaways = (orders.data ?? []).filter((o) => o.type === 'TAKEAWAY')

  const onTableClick = (table: DiningTable) => {
    if (table.openOrderId) {
      navigate(`/orders/${table.openOrderId}`)
    } else {
      setGuestCount(Math.min(2, table.seats))
      setOpening(table)
    }
  }

  return (
    <>
      <div className="page-title">
        <Typography.Title level={3}>Sơ đồ bàn</Typography.Title>
        <Button icon={<ShoppingOutlined />} loading={createOrder.isPending} onClick={() => createOrder.mutate({ type: 'TAKEAWAY' })}>
          Đơn mang về
        </Button>
      </div>

      {[...byArea.entries()].map(([area, list]) => (
        <div key={area} style={{ marginBottom: 24 }}>
          <Typography.Title level={5}>{area}</Typography.Title>
          <Row gutter={[12, 12]}>
            {list.map((t) => (
              <Col key={t.id} xs={12} sm={8} md={6} xl={4}>
                <Card
                  size="small"
                  className={`table-card ${t.status === 'OCCUPIED' ? 'occupied' : ''}`}
                  onClick={() => onTableClick(t)}
                >
                  <Flex justify="space-between" align="center">
                    <Typography.Title level={4} style={{ margin: 0 }}>
                      {t.name}
                    </Typography.Title>
                    <Tag color={t.status === 'OCCUPIED' ? 'orange' : 'green'}>
                      {t.status === 'OCCUPIED' ? 'Có khách' : 'Trống'}
                    </Tag>
                  </Flex>
                  <Typography.Text type="secondary">
                    {t.status === 'OCCUPIED' && t.guestCount ? `${t.guestCount} khách · ` : ''}
                    {t.seats} ghế
                  </Typography.Text>
                  <Flex vertical gap={4} style={{ marginTop: 6 }}>
                    {t.pendingCount > 0 && <Tag color="gold">{t.pendingCount} món QR chờ xác nhận</Tag>}
                    {t.readyCount > 0 && <Tag color="green">{t.readyCount} món xong, mang ra</Tag>}
                  </Flex>
                </Card>
              </Col>
            ))}
          </Row>
        </div>
      ))}

      <Typography.Title level={5}>Đơn mang về đang mở</Typography.Title>
      {takeaways.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Không có" />
      ) : (
        <Row gutter={[12, 12]}>
          {takeaways.map((o) => (
            <Col key={o.id} xs={12} sm={8} md={6} xl={4}>
              <Card size="small" className="table-card occupied" onClick={() => navigate(`/orders/${o.id}`)}>
                <Typography.Text strong>Mang về #{o.id}</Typography.Text>
                <div>{money(o.total)}</div>
              </Card>
            </Col>
          ))}
        </Row>
      )}

      <Modal
        title={`Mở bàn ${opening?.name ?? ''}`}
        open={opening !== null}
        okText="Mở bàn"
        confirmLoading={createOrder.isPending}
        onCancel={() => setOpening(null)}
        onOk={() => opening && createOrder.mutate({ type: 'DINE_IN', tableId: opening.id, guestCount })}
        destroyOnHidden
      >
        <Typography.Paragraph>Số khách</Typography.Paragraph>
        <InputNumber min={1} max={100} value={guestCount} onChange={(v) => setGuestCount(v ?? 1)} size="large" />
      </Modal>
    </>
  )
}
