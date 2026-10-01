import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Card, Col, DatePicker, Row, Statistic, Table, Typography } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { api } from '@/shared/api/client'
import type { ReportSummary } from '@/shared/api/types'
import { money } from '@/shared/utils/format'

const METHOD_LABEL = { CASH: 'Tiền mặt', BANK_TRANSFER: 'Chuyển khoản' } as const

/** FR-10: revenue by day and payment method, top dishes (BR-21). */
export default function ReportsPage() {
  const [range, setRange] = useState<[Dayjs, Dayjs]>([dayjs().subtract(6, 'day'), dayjs()])
  const from = range[0].format('YYYY-MM-DD')
  const to = range[1].format('YYYY-MM-DD')

  const report = useQuery({
    queryKey: ['report', from, to],
    queryFn: () => api.get<ReportSummary>('/reports/summary', { params: { from, to } }).then((r) => r.data),
  })
  const data = report.data
  const maxDay = Math.max(1, ...(data?.byDay.map((d) => d.amount) ?? []))

  return (
    <>
      <div className="page-title">
        <Typography.Title level={3}>Báo cáo</Typography.Title>
        <DatePicker.RangePicker
          value={range}
          format="DD/MM/YYYY"
          allowClear={false}
          onChange={(value) => value && value[0] && value[1] && setRange([value[0], value[1]])}
        />
      </div>
      <Row gutter={[16, 16]}>
        <Col xs={24} md={8}>
          <Card loading={report.isLoading}>
            <Statistic title="Doanh thu" value={data?.revenue ?? 0} formatter={(v) => money(Number(v))} />
          </Card>
        </Col>
        <Col xs={12} md={8}>
          <Card loading={report.isLoading}>
            <Statistic title="Số đơn đã thanh toán" value={data?.orderCount ?? 0} />
          </Card>
        </Col>
        <Col xs={12} md={8}>
          <Card loading={report.isLoading}>
            <Statistic title="Trung bình mỗi đơn" value={data?.averagePerOrder ?? 0} formatter={(v) => money(Number(v))} />
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card title="Doanh thu theo ngày" size="small">
            <Table
              size="small"
              rowKey="date"
              pagination={false}
              dataSource={data?.byDay ?? []}
              columns={[
                { title: 'Ngày', render: (_, d) => dayjs(d.date).format('DD/MM') },
                { title: 'Số đơn', dataIndex: 'count', width: 70 },
                { title: 'Doanh thu', render: (_, d) => money(d.amount) },
                {
                  title: '',
                  width: '35%',
                  render: (_, d) => <div className="bar" style={{ width: `${(d.amount / maxDay) * 100}%` }} />,
                },
              ]}
            />
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card title="Theo phương thức" size="small" style={{ marginBottom: 16 }}>
            <Table
              size="small"
              rowKey="method"
              pagination={false}
              dataSource={data?.byMethod ?? []}
              columns={[
                { title: 'Phương thức', render: (_, m) => METHOD_LABEL[m.method] },
                { title: 'Số đơn', dataIndex: 'count' },
                { title: 'Doanh thu', render: (_, m) => money(m.amount) },
              ]}
            />
          </Card>
          <Card title="Top 10 món bán chạy" size="small">
            <Table
              size="small"
              rowKey="itemName"
              pagination={false}
              dataSource={data?.topItems ?? []}
              columns={[
                { title: 'Món', dataIndex: 'itemName' },
                { title: 'Số lượng', dataIndex: 'quantity' },
                { title: 'Doanh thu', render: (_, t) => money(t.amount) },
              ]}
            />
          </Card>
        </Col>
      </Row>
    </>
  )
}
