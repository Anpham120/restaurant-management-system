import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Alert, DatePicker, Flex, Table, Typography } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { api, errorMessage } from '@/shared/api/client'
import type { StockUsage } from '@/shared/api/types'
import { countDifference } from '../utils/usage'

/** FR-09.10: what dishes used by their recipes, beside what left by hand and what counts found (BR-38). */
export default function UsageTab() {
  const [range, setRange] = useState<[Dayjs, Dayjs]>([dayjs().subtract(6, 'day'), dayjs()])
  const from = range[0].format('YYYY-MM-DD')
  const to = range[1].format('YYYY-MM-DD')

  const usage = useQuery({
    queryKey: ['inventory-usage', from, to],
    queryFn: () => api.get<StockUsage[]>('/inventory-usage', { params: { from, to } }).then((r) => r.data),
  })

  return (
    <>
      <Flex justify="space-between" align="center" gap={8} wrap style={{ marginBottom: 16 }}>
        <DatePicker.RangePicker
          value={range}
          format="DD/MM/YYYY"
          allowClear={false}
          onChange={(value) => value && value[0] && value[1] && setRange([value[0], value[1]])}
        />
        <Typography.Text type="secondary">Theo định lượng: lượng món vào bếp đã trừ, bớt phần món huỷ được hoàn.</Typography.Text>
      </Flex>
      {usage.isError && <Alert type="error" showIcon style={{ marginBottom: 16 }} title={errorMessage(usage.error)} />}
      <Table<StockUsage>
        size="small"
        rowKey="inventoryItemId"
        loading={usage.isLoading}
        dataSource={usage.data ?? []}
        scroll={{ x: 640 }}
        columns={[
          { title: 'Nguyên liệu', dataIndex: 'name' },
          { title: 'Theo định lượng', render: (_, u) => `${u.used} ${u.unit}` },
          { title: 'Xuất tay', render: (_, u) => `${u.removed} ${u.unit}` },
          {
            title: 'Kiểm kê',
            render: (_, u) => (
              <Typography.Text type={u.adjusted < 0 ? 'danger' : undefined}>{countDifference(u.adjusted, u.unit)}</Typography.Text>
            ),
          },
        ]}
      />
    </>
  )
}
