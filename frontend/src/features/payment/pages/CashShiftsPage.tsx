import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Alert, DatePicker, Table, Tag, Typography } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { api, errorMessage } from '@/shared/api/client'
import type { CashExpense, CashShift } from '@/shared/api/types'
import { money, time } from '@/shared/utils/format'
import { differenceText } from '../utils/cashShift'

/** FR-17.4: drawer shifts by the day they opened, with what was expected, counted and why they differ (BR-39). */
export default function CashShiftsPage() {
  const [range, setRange] = useState<[Dayjs, Dayjs]>([dayjs().subtract(6, 'day'), dayjs()])
  const from = range[0].format('YYYY-MM-DD')
  const to = range[1].format('YYYY-MM-DD')

  const shifts = useQuery({
    queryKey: ['cash-shifts', from, to],
    queryFn: () => api.get<CashShift[]>('/cash-shifts', { params: { from, to } }).then((r) => r.data),
  })

  return (
    <>
      <div className="page-title">
        <Typography.Title level={3}>Ca két</Typography.Title>
        <DatePicker.RangePicker
          value={range}
          format="DD/MM/YYYY"
          allowClear={false}
          onChange={(value) => value && value[0] && value[1] && setRange([value[0], value[1]])}
        />
      </div>
      {shifts.isError && <Alert type="error" showIcon style={{ marginBottom: 16 }} title={errorMessage(shifts.error)} />}
      <Table<CashShift>
        size="small"
        rowKey="id"
        loading={shifts.isLoading}
        dataSource={shifts.data ?? []}
        scroll={{ x: 1100 }}
        expandable={{
          rowExpandable: (s) => s.expenses.length > 0,
          expandedRowRender: (s) => (
            <Table<CashExpense>
              size="small"
              rowKey="id"
              pagination={false}
              dataSource={s.expenses}
              columns={[
                { title: 'Lúc', render: (_, e) => time(e.createdAt) },
                { title: 'Phiếu chi', dataIndex: 'reason' },
                { title: 'Người chi', dataIndex: 'createdByName' },
                { title: 'Số tiền', render: (_, e) => money(e.amount) },
              ]}
            />
          ),
        }}
        columns={[
          { title: 'Ca', render: (_, s) => `#${s.id}` },
          { title: 'Mở', render: (_, s) => `${time(s.openedAt)} · ${s.openedByName}` },
          { title: 'Quỹ đầu ca', render: (_, s) => money(s.openingFloat) },
          { title: 'Thu tiền mặt', render: (_, s) => `${money(s.cashTaken)} (${s.cashPayments})` },
          { title: 'Phiếu chi', render: (_, s) => money(s.expenseTotal) },
          { title: 'Dự kiến', render: (_, s) => money(s.expectedCash) },
          { title: 'Thực đếm', render: (_, s) => (s.countedCash === null ? '' : money(s.countedCash)) },
          {
            title: 'Chênh lệch',
            render: (_, s) =>
              s.difference === null ? (
                <Tag color="blue">Đang mở</Tag>
              ) : (
                <Typography.Text type={s.difference === 0 ? undefined : 'danger'}>{differenceText(s.difference)}</Typography.Text>
              ),
          },
          { title: 'Lý do', dataIndex: 'closeNote' },
          { title: 'Chốt', render: (_, s) => (s.closedAt ? `${time(s.closedAt)} · ${s.closedByName}` : '') },
        ]}
      />
    </>
  )
}
