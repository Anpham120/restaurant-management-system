import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Badge, Button, Card, Drawer, Empty, Flex, Typography } from 'antd'
import { AuditOutlined } from '@ant-design/icons'
import { api, errorMessage } from '@/shared/api/client'
import type { Adjustment } from '@/shared/api/types'
import { money, time } from '@/shared/utils/format'
import { adjustmentLabel, adjustmentReason } from '../utils/adjustment'

/** FR-08.11: discounts past the limit, counted in the header of a manager's screens and decided in a drawer. */
export default function ApprovalsButton() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  const [open, setOpen] = useState(false)

  const pending = useQuery({
    queryKey: ['adjustments', 'PENDING'],
    queryFn: () => api.get<Adjustment[]>('/adjustments', { params: { status: 'PENDING' } }).then((r) => r.data),
  })
  const decide = useMutation({
    mutationFn: ({ id, approve }: { id: number; approve: boolean }) =>
      api.post(`/adjustments/${id}/${approve ? 'approve' : 'reject'}`),
    onSuccess: (_, { approve }) => message.success(approve ? 'Đã duyệt' : 'Đã từ chối'),
    onError: (e) => message.error(errorMessage(e)),
    // Also after an error: another manager may have decided it first.
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['adjustments'] }),
  })
  const list = pending.data ?? []

  return (
    <>
      <Badge count={list.length} size="small">
        <Button icon={<AuditOutlined />} title="Duyệt giảm giá" onClick={() => setOpen(true)} />
      </Badge>
      <Drawer title="Duyệt giảm giá" open={open} onClose={() => setOpen(false)} size="min(100vw, 400px)">
        {list.length === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Không có khoản nào chờ duyệt" />
        ) : (
          <Flex vertical gap={8}>
            {list.map((a) => (
              <Card key={a.id} size="small">
                <Flex vertical gap={4}>
                  <Typography.Text strong>
                    {a.tableName ? `Bàn ${a.tableName}` : 'Mang về'} · Đơn #{a.orderId}
                  </Typography.Text>
                  <span>
                    {adjustmentLabel(a)}: <Typography.Text strong>{money(a.amount)}</Typography.Text>
                  </span>
                  <Typography.Text type="secondary">
                    {adjustmentReason(a)} · {a.createdByName} · {time(a.createdAt)}
                  </Typography.Text>
                  <Flex gap={8} justify="flex-end">
                    <Button danger loading={decide.isPending && decide.variables?.id === a.id && !decide.variables.approve} onClick={() => decide.mutate({ id: a.id, approve: false })}>
                      Từ chối
                    </Button>
                    <Button type="primary" loading={decide.isPending && decide.variables?.id === a.id && decide.variables.approve} onClick={() => decide.mutate({ id: a.id, approve: true })}>
                      Duyệt
                    </Button>
                  </Flex>
                </Flex>
              </Card>
            ))}
          </Flex>
        )}
      </Drawer>
    </>
  )
}
