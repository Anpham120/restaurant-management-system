import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Alert, Button, DatePicker, Flex, Table, Typography } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import dayjs, { type Dayjs } from 'dayjs'
import { api, errorMessage } from '@/shared/api/client'
import type { GoodsReceipt, InventoryItem, ReceiptLine } from '@/shared/api/types'
import { money, time } from '@/shared/utils/format'
import ReceiptModal from './ReceiptModal'

/** FR-09.6: receipts by day range, newest first; the backend looks at no more than 92 days at once. */
export default function ReceiptsTab({ items }: { items: InventoryItem[] }) {
  const [range, setRange] = useState<[Dayjs, Dayjs]>([dayjs().subtract(29, 'day'), dayjs()])
  const [creating, setCreating] = useState(false)
  const from = range[0].format('YYYY-MM-DD')
  const to = range[1].format('YYYY-MM-DD')

  const receipts = useQuery({
    queryKey: ['goods-receipts', from, to],
    queryFn: () => api.get<GoodsReceipt[]>('/goods-receipts', { params: { from, to } }).then((r) => r.data),
  })
  const data = receipts.data ?? []

  return (
    <>
      <Flex justify="space-between" gap={8} wrap style={{ marginBottom: 16 }}>
        <DatePicker.RangePicker
          value={range}
          format="DD/MM/YYYY"
          allowClear={false}
          onChange={(value) => value && value[0] && value[1] && setRange([value[0], value[1]])}
        />
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreating(true)}>
          Lập phiếu nhập
        </Button>
      </Flex>
      {receipts.isError && <Alert type="error" showIcon style={{ marginBottom: 16 }} title={errorMessage(receipts.error)} />}
      <Table<GoodsReceipt>
        size="small"
        rowKey="id"
        loading={receipts.isLoading}
        dataSource={data}
        scroll={{ x: 760 }}
        expandable={{
          expandedRowRender: (r) => (
            <Table<ReceiptLine>
              size="small"
              rowKey="id"
              pagination={false}
              dataSource={r.lines}
              columns={[
                { title: 'Nguyên liệu', dataIndex: 'itemName' },
                { title: 'Số lượng', render: (_, l) => `${l.quantity} ${l.unit}` },
                { title: 'Đơn giá', render: (_, l) => `${money(l.unitPrice)}/${l.unit}` },
                { title: 'Thành tiền', render: (_, l) => money(l.lineTotal) },
              ]}
            />
          ),
        }}
        columns={[
          { title: 'Số phiếu', render: (_, r) => `#${r.id}` },
          { title: 'Thời gian', render: (_, r) => time(r.createdAt) },
          { title: 'Nhà cung cấp', dataIndex: 'supplierName' },
          { title: 'Số dòng', render: (_, r) => r.lines.length },
          { title: 'Tổng tiền', render: (_, r) => money(r.total) },
          { title: 'Người lập', dataIndex: 'createdByName' },
          { title: 'Ghi chú', dataIndex: 'note' },
        ]}
      />
      <Typography.Text strong>
        Tổng chi {data.length} phiếu: {money(data.reduce((sum, r) => sum + r.total, 0))}
      </Typography.Text>
      <ReceiptModal open={creating} onClose={() => setCreating(false)} items={items} />
    </>
  )
}
