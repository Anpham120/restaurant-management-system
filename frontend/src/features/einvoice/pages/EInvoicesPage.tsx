import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App, Button, DatePicker, Flex, Segmented, Space, Statistic, Table, Tag, Typography } from 'antd'
import { DownloadOutlined } from '@ant-design/icons'
import dayjs, { type Dayjs } from 'dayjs'
import { api, errorMessage } from '@/shared/api/client'
import type { EInvoice, EInvoiceStatus } from '@/shared/api/types'
import { money } from '@/shared/utils/format'
import { downloadBlob } from '@/shared/utils/sheet'
import BuyerModal from '../components/BuyerModal'
import InvoiceLines from '../components/InvoiceLines'
import NumberModal from '../components/NumberModal'
import { statusColor, statusLabel } from '../utils/einvoice'

type Filter = EInvoiceStatus | 'ALL'

const sum = (list: EInvoice[], key: 'beforeTax' | 'taxAmount' | 'total') => list.reduce((n, i) => n + i[key], 0)

/** FR-20.2 → FR-20.4: the e-invoices of some days; those without a number go to a file for MISA, then get theirs. */
export default function EInvoicesPage() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  const [range, setRange] = useState<[Dayjs, Dayjs]>([dayjs(), dayjs()])
  const [filter, setFilter] = useState<Filter>('ALL')
  const [buyerOf, setBuyerOf] = useState<number | null>(null)
  const [numberOf, setNumberOf] = useState<EInvoice | null>(null)
  const from = range[0].format('YYYY-MM-DD')
  const to = range[1].format('YYYY-MM-DD')

  const invoices = useQuery({
    queryKey: ['einvoices', from, to],
    queryFn: () => api.get<EInvoice[]>('/einvoices', { params: { from, to } }).then((r) => r.data),
  })
  const all = invoices.data ?? []
  const list = all.filter((i) => filter === 'ALL' || i.status === filter)
  const count = (status: EInvoiceStatus) => all.filter((i) => i.status === status).length
  const toExport = count('PENDING') + count('EXPORTED')
  const lastSymbol = [...all].reverse().find((i) => i.invoiceSymbol)?.invoiceSymbol ?? undefined

  const exportFile = useMutation({
    mutationFn: () => api.post<Blob>('/einvoices/export', { from, to }, { responseType: 'blob' }).then((r) => r.data),
    onSuccess: (file) => {
      downloadBlob(`hoa-don-${from}-${to}.xlsx`, file)
      message.success(`Đã xuất ${toExport} hoá đơn chưa có số`)
      queryClient.invalidateQueries({ queryKey: ['einvoices'] })
    },
    onError: () => message.error('Không xuất được file, thử lại sau'),
  })

  return (
    <>
      <div className="page-title">
        <Typography.Title level={3}>Hoá đơn điện tử</Typography.Title>
        <Flex gap={8} wrap>
          <DatePicker.RangePicker
            value={range}
            format="DD/MM/YYYY"
            allowClear={false}
            onChange={(values) => values?.[0] && values[1] && setRange([values[0], values[1]])}
          />
          <Button
            type="primary"
            icon={<DownloadOutlined />}
            disabled={toExport === 0}
            loading={exportFile.isPending}
            onClick={() => exportFile.mutate()}
          >
            Xuất file MISA ({toExport})
          </Button>
        </Flex>
      </div>
      {invoices.isError && <Alert type="error" showIcon style={{ marginBottom: 16 }} title={errorMessage(invoices.error)} />}
      <Flex gap={32} wrap style={{ marginBottom: 16 }}>
        <Statistic title="Số hoá đơn" value={list.length} />
        <Statistic title="Tiền chưa thuế" value={money(sum(list, 'beforeTax'))} />
        <Statistic title="Tiền thuế" value={money(sum(list, 'taxAmount'))} />
        <Statistic title="Tổng thanh toán" value={money(sum(list, 'total'))} />
      </Flex>
      <Segmented<Filter>
        value={filter}
        onChange={setFilter}
        style={{ marginBottom: 12, maxWidth: '100%', overflowX: 'auto' }}
        options={[
          { value: 'ALL', label: `Tất cả (${all.length})` },
          { value: 'PENDING', label: `${statusLabel.PENDING} (${count('PENDING')})` },
          { value: 'EXPORTED', label: `${statusLabel.EXPORTED} (${count('EXPORTED')})` },
          { value: 'ISSUED', label: `${statusLabel.ISSUED} (${count('ISSUED')})` },
        ]}
      />
      <Table<EInvoice>
        size="small"
        rowKey="id"
        loading={invoices.isLoading}
        dataSource={list}
        pagination={{ pageSize: 50, hideOnSinglePage: true }}
        scroll={{ x: 'max-content' }}
        expandable={{ expandedRowRender: (i) => <InvoiceLines id={i.id} /> }}
        columns={[
          { title: 'Ngày', render: (_, i) => dayjs(i.invoiceDate).format('HH:mm DD/MM/YYYY') },
          { title: 'Đơn', render: (_, i) => `#${i.orderId}` },
          {
            title: 'Người mua',
            render: (_, i) =>
              i.buyerName ? (
                <>
                  <div>{i.buyerName}</div>
                  {i.buyerTaxCode && <Typography.Text type="secondary">MST {i.buyerTaxCode}</Typography.Text>}
                </>
              ) : (
                <Typography.Text type="secondary">Khách lẻ</Typography.Text>
              ),
          },
          { title: 'Chưa thuế', render: (_, i) => money(i.beforeTax) },
          { title: 'Tiền thuế', render: (_, i) => money(i.taxAmount) },
          { title: 'Tổng', render: (_, i) => money(i.total) },
          { title: 'Thanh toán', dataIndex: 'paymentMethod' },
          {
            title: 'Trạng thái',
            render: (_, i) => (
              <Space size={4} wrap>
                <Tag color={statusColor[i.status]}>{statusLabel[i.status]}</Tag>
                {i.invoiceNo && `${i.invoiceSymbol} ${i.invoiceNo}`}
              </Space>
            ),
          },
          {
            title: '',
            fixed: 'right',
            render: (_, i) => (
              <Space wrap>
                <Button size="small" disabled={i.status === 'ISSUED'} onClick={() => setBuyerOf(i.orderId)}>
                  Người mua
                </Button>
                <Button size="small" onClick={() => setNumberOf(i)}>
                  {i.invoiceNo ? 'Sửa số' : 'Ghi số'}
                </Button>
              </Space>
            ),
          },
        ]}
      />
      {buyerOf !== null && <BuyerModal orderId={buyerOf} onClose={() => setBuyerOf(null)} />}
      {numberOf && <NumberModal invoice={numberOf} symbol={lastSymbol} onClose={() => setNumberOf(null)} />}
    </>
  )
}
