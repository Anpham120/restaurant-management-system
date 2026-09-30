import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Button, Card, Col, Empty, Flex, Row, Switch, Tag, Typography } from 'antd'
import { api, errorMessage } from '../api/client'
import type { ItemStatus, KitchenItem, MenuItem, Settings } from '../api/types'
import { minutesSince } from '../utils/format'

const COLUMNS: { status: ItemStatus; title: string; next?: ItemStatus; action?: string }[] = [
  { status: 'WAITING', title: 'Chờ làm', next: 'COOKING', action: 'Bắt đầu' },
  { status: 'COOKING', title: 'Đang làm', next: 'READY', action: 'Xong' },
  { status: 'READY', title: 'Xong, chờ phục vụ ra' },
]

/** FR-07: kitchen display; the oldest dish is on top (US-12), a late dish is red (BR-28). */
export default function KitchenPage() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 30_000)
    return () => clearInterval(timer)
  }, [])

  const items = useQuery({ queryKey: ['kitchen'], queryFn: () => api.get<KitchenItem[]>('/kitchen/items').then((r) => r.data) })
  const menu = useQuery({ queryKey: ['menu-items'], queryFn: () => api.get<MenuItem[]>('/menu-items').then((r) => r.data) })
  const settings = useQuery({ queryKey: ['settings'], queryFn: () => api.get<Settings>('/settings').then((r) => r.data) })
  const lateAfter = settings.data?.waitAlertMinutes

  const advance = useMutation({
    mutationFn: ({ id, status }: { id: number; status: ItemStatus }) => api.patch(`/order-items/${id}/status`, { status }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['kitchen'] }),
    onError: (e) => message.error(errorMessage(e)),
  })
  const setAvailable = useMutation({
    mutationFn: ({ id, available }: { id: number; available: boolean }) => api.patch(`/menu-items/${id}/availability`, { available }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['menu-items'] }),
    onError: (e) => message.error(errorMessage(e)),
  })

  return (
    <>
      <div className="page-title">
        <Typography.Title level={3}>Màn hình bếp</Typography.Title>
      </div>
      <Row gutter={[12, 12]}>
        {COLUMNS.map((column) => {
          const list = (items.data ?? []).filter((i) => i.status === column.status)
          return (
            <Col key={column.status} xs={24} md={8}>
              <div className="kitchen-column">
                <Typography.Title level={5}>
                  {column.title} ({list.length})
                </Typography.Title>
                {list.length === 0 && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Trống" />}
                <Flex vertical gap={8}>
                  {list.map((item) => {
                    const waited = minutesSince(item.sentAt, now)
                    const late = lateAfter !== undefined && waited >= lateAfter
                    return (
                      <Card key={item.id} size="small" className={late ? 'late' : undefined}>
                        <Flex justify="space-between" align="center">
                          <Typography.Text strong>{item.tableName ?? `Mang về #${item.orderId}`}</Typography.Text>
                          <Tag color={late ? 'red' : 'default'}>{waited} phút</Tag>
                        </Flex>
                        <Typography.Title level={5} style={{ margin: '4px 0' }}>
                          {item.itemName} × {item.quantity}
                        </Typography.Title>
                        {item.note && <div className="note">{item.note}</div>}
                        {column.next && (
                          <Button
                            type="primary"
                            block
                            style={{ marginTop: 8 }}
                            loading={advance.isPending && advance.variables?.id === item.id}
                            onClick={() => advance.mutate({ id: item.id, status: column.next! })}
                          >
                            {column.action}
                          </Button>
                        )}
                      </Card>
                    )
                  })}
                </Flex>
              </div>
            </Col>
          )
        })}
      </Row>

      <Card title="Tình trạng món (tắt = hết món)" size="small" style={{ marginTop: 16 }}>
        <Row gutter={[12, 8]}>
          {(menu.data ?? []).map((m) => (
            <Col key={m.id} xs={24} sm={12} lg={8}>
              <Flex justify="space-between" align="center">
                <Typography.Text delete={!m.available}>{m.name}</Typography.Text>
                <Switch
                  checked={m.available}
                  checkedChildren="Còn"
                  unCheckedChildren="Hết"
                  onChange={(available) => setAvailable.mutate({ id: m.id, available })}
                />
              </Flex>
            </Col>
          ))}
        </Row>
      </Card>
    </>
  )
}
