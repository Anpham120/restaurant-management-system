import { useQuery } from '@tanstack/react-query'
import { Flex, Spin, Table, Typography } from 'antd'
import { api } from '@/shared/api/client'
import type { EInvoiceDetail, EInvoiceLine } from '@/shared/api/types'
import { money } from '@/shared/utils/format'

/** FR-20.2: the lines of an e-invoice, a discount taken off, and what each tax rate comes to. */
export default function InvoiceLines({ id }: { id: number }) {
  const detail = useQuery({
    queryKey: ['einvoice', id],
    queryFn: () => api.get<EInvoiceDetail>(`/einvoices/${id}`).then((r) => r.data),
  })
  if (detail.isLoading) return <Spin size="small" />
  const d = detail.data
  if (!d) return null
  const signed = (l: EInvoiceLine, amount: number) => (l.kind === 'DISCOUNT' ? -amount : amount)

  return (
    <Flex vertical gap={8}>
      <Table<EInvoiceLine>
        size="small"
        rowKey="lineNo"
        pagination={false}
        dataSource={d.lines}
        scroll={{ x: 'max-content' }}
        columns={[
          { title: 'Tên hàng hoá, dịch vụ', dataIndex: 'itemName' },
          { title: 'ĐVT', render: (_, l) => l.unit ?? '' },
          { title: 'SL', render: (_, l) => l.quantity ?? '' },
          { title: 'Đơn giá gồm thuế', render: (_, l) => (l.unitPrice === null ? '' : money(l.unitPrice)) },
          { title: 'Thuế suất', render: (_, l) => `${l.taxRate}%` },
          { title: 'Chưa thuế', render: (_, l) => money(signed(l, l.beforeTax)) },
          { title: 'Tiền thuế', render: (_, l) => money(signed(l, l.taxAmount)) },
          { title: 'Thành tiền', render: (_, l) => money(signed(l, l.amount)) },
        ]}
      />
      <Typography.Text type="secondary">
        {d.taxes.map((t) => `Thuế suất ${t.taxRate}%: chưa thuế ${money(t.beforeTax)}, thuế ${money(t.taxAmount)}`).join('; ')}
      </Typography.Text>
    </Flex>
  )
}
