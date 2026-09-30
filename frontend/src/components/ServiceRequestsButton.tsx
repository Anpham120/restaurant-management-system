import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Badge, Button, Card, Drawer, Empty, Flex, Tag, Typography } from 'antd'
import { BellOutlined } from '@ant-design/icons'
import { api, errorMessage } from '../api/client'
import type { ServiceRequest } from '../api/types'
import { minutesSince, requestTypeLabel } from '../utils/format'

/** Guests calling from their table (FR-06.6, FR-06.7): the count in the header, the calls in a drawer. */
export default function ServiceRequestsButton() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  const [open, setOpen] = useState(false)
  const [now, setNow] = useState(() => Date.now())

  // Keeps the minutes waited current while the drawer is open.
  useEffect(() => {
    if (!open) return
    const timer = setInterval(() => setNow(Date.now()), 30_000)
    return () => clearInterval(timer)
  }, [open])

  const requests = useQuery({
    queryKey: ['service-requests'],
    queryFn: () => api.get<ServiceRequest[]>('/service-requests').then((r) => r.data),
  })
  const take = useMutation({
    mutationFn: (id: number) => api.post(`/service-requests/${id}/take`),
    onError: (e) => message.error(errorMessage(e)),
    // Also after an error: another waiter may have taken the call first.
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['service-requests'] }),
  })
  const list = requests.data ?? []

  return (
    <>
      <Badge count={list.length} size="small">
        <Button
          icon={<BellOutlined />}
          title="Khách gọi"
          onClick={() => {
            setNow(Date.now())
            setOpen(true)
          }}
        />
      </Badge>
      <Drawer title="Khách gọi" open={open} onClose={() => setOpen(false)} size="min(100vw, 400px)">
        {list.length === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Không có bàn nào đang gọi" />
        ) : (
          <Flex vertical gap={8}>
            {list.map((r) => (
              <Card key={r.id} size="small">
                <Flex justify="space-between" align="center" gap={8}>
                  <div>
                    <Typography.Text strong>Bàn {r.tableName}</Typography.Text>
                    <Flex gap={8} align="center">
                      <Tag color={r.type === 'BILL' ? 'gold' : 'blue'}>{requestTypeLabel[r.type]}</Tag>
                      <Typography.Text type="secondary">{minutesSince(r.createdAt, now)} phút</Typography.Text>
                    </Flex>
                  </div>
                  <Button
                    type="primary"
                    loading={take.isPending && take.variables === r.id}
                    onClick={() => take.mutate(r.id)}
                  >
                    Đã nhận
                  </Button>
                </Flex>
              </Card>
            ))}
          </Flex>
        )}
      </Drawer>
    </>
  )
}
