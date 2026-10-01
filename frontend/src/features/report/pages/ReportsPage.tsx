import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Button, Card, Col, DatePicker, Flex, Row, Statistic, Table, Tag, Typography } from 'antd'
import { DownloadOutlined } from '@ant-design/icons'
import dayjs, { type Dayjs } from 'dayjs'
import { api } from '@/shared/api/client'
import type { DishProfit, ExceptionsReport, GrossProfitReport, ReportSummary } from '@/shared/api/types'
import { money } from '@/shared/utils/format'
import { downloadSheet } from '@/shared/utils/sheet'
import { auditActionLabel } from '@/features/audit/utils/audit'
import DayChart from '../components/DayChart'
import { everyDay, methodLabel, profitRate, reportSheet } from '../utils/report'

/** FR-10: revenue by day and payment method, top dishes (BR-21); gross profit by dish, exceptions, Excel (BR-40). */
export default function ReportsPage() {
  const [range, setRange] = useState<[Dayjs, Dayjs]>([dayjs().subtract(6, 'day'), dayjs()])
  const from = range[0].format('YYYY-MM-DD')
  const to = range[1].format('YYYY-MM-DD')
  const params = { params: { from, to } }

  const report = useQuery({
    queryKey: ['report', from, to],
    queryFn: () => api.get<ReportSummary>('/reports/summary', params).then((r) => r.data),
  })
  const profit = useQuery({
    queryKey: ['report-profit', from, to],
    queryFn: () => api.get<GrossProfitReport>('/reports/gross-profit', params).then((r) => r.data),
  })
  const exceptions = useQuery({
    queryKey: ['report-exceptions', from, to],
    queryFn: () => api.get<ExceptionsReport>('/reports/exceptions', params).then((r) => r.data),
  })
  const data = report.data

  return (
    <>
      <div className="page-title">
        <Typography.Title level={3}>Báo cáo</Typography.Title>
        <Flex gap={8} wrap>
          <DatePicker.RangePicker
            value={range}
            format="DD/MM/YYYY"
            allowClear={false}
            onChange={(value) => value && value[0] && value[1] && setRange([value[0], value[1]])}
          />
          <Button
            icon={<DownloadOutlined />}
            disabled={!data || !profit.data || !exceptions.data}
            onClick={() => data && profit.data && exceptions.data && downloadSheet(`bao-cao-${from}-${to}.csv`, reportSheet(data, profit.data, exceptions.data))}
          >
            Xuất Excel
          </Button>
        </Flex>
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
            {data && <DayChart days={everyDay(data.from, data.to, data.byDay)} />}
            <Table
              size="small"
              rowKey="date"
              pagination={false}
              dataSource={data?.byDay ?? []}
              style={{ marginTop: 12 }}
              columns={[
                { title: 'Ngày', render: (_, d) => dayjs(d.date).format('DD/MM') },
                { title: 'Số đơn', dataIndex: 'count', width: 70 },
                { title: 'Doanh thu', render: (_, d) => money(d.amount) },
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
                { title: 'Phương thức', render: (_, m) => methodLabel[m.method] },
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
        <Col xs={24}>
          <Card title="Lãi gộp theo món" size="small" loading={profit.isLoading}>
            {profit.data && (
              <Row gutter={[16, 8]} style={{ marginBottom: 12 }}>
                <Col xs={12} md={6}><Statistic title="Giảm giá, tặng món" value={money(profit.data.discounts)} /></Col>
                <Col xs={12} md={6}><Statistic title="Doanh thu món đủ giá vốn" value={money(profit.data.costedRevenue)} /></Col>
                <Col xs={12} md={6}><Statistic title="Giá vốn" value={money(profit.data.cost)} /></Col>
                <Col xs={12} md={6}><Statistic title="Lãi gộp" value={money(profit.data.grossProfit)} /></Col>
              </Row>
            )}
            <Table<DishProfit>
              size="small"
              rowKey="itemName"
              dataSource={profit.data?.dishes ?? []}
              scroll={{ x: 640 }}
              columns={[
                { title: 'Món', dataIndex: 'itemName' },
                { title: 'Số lượng', dataIndex: 'quantity' },
                { title: 'Doanh thu', render: (_, d) => money(d.revenue) },
                { title: 'Giá vốn', render: (_, d) => (d.cost === null ? <Tag>Chưa đủ</Tag> : money(d.cost)) },
                { title: 'Lãi gộp', render: (_, d) => (d.grossProfit === null ? '' : money(d.grossProfit)) },
                { title: 'Tỷ lệ', render: (_, d) => (profitRate(d) === null ? '' : `${profitRate(d)}%`) },
              ]}
            />
            <Typography.Text type="secondary">
              Giá vốn theo định lượng lúc món vào bếp. "Chưa đủ": món chưa có định lượng, hoặc có nguyên liệu chưa nhập theo phiếu có giá.
            </Typography.Text>
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card title="Ngoại lệ" size="small" loading={exceptions.isLoading}>
            <Table
              size="small"
              rowKey="action"
              pagination={false}
              dataSource={exceptions.data?.byAction ?? []}
              columns={[
                { title: 'Loại', render: (_, a) => auditActionLabel[a.action] },
                { title: 'Số lần', dataIndex: 'count' },
                { title: 'Số tiền', render: (_, a) => money(a.amount) },
              ]}
            />
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card title="Ngoại lệ theo người" size="small" loading={exceptions.isLoading}>
            <Table
              size="small"
              rowKey={(p) => `${p.employeeId}-${p.action}`}
              pagination={false}
              dataSource={exceptions.data?.byPerson ?? []}
              columns={[
                { title: 'Người làm', dataIndex: 'employeeName' },
                { title: 'Loại', render: (_, p) => auditActionLabel[p.action] },
                { title: 'Số lần', dataIndex: 'count' },
                { title: 'Số tiền', render: (_, p) => money(p.amount) },
              ]}
            />
          </Card>
        </Col>
      </Row>
    </>
  )
}
