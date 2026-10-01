import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { DatePicker, Flex, Input, Select, Table, Tag, Typography } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { api } from '@/shared/api/client'
import type { AuditAction, AuditEntry } from '@/shared/api/types'
import { money, time } from '@/shared/utils/format'
import { auditActionLabel, auditChange, auditOrder } from '../utils/audit'

const ACTION_COLOR: Record<AuditAction, string> = { ITEM_CANCELLED: 'red', MANUAL_CONFIRMATION: 'gold', PRICE_CHANGED: 'blue' }

/** FR-16: who cancelled dishes, confirmed transfers by hand or changed prices (BR-34). Read only. */
export default function AuditPage() {
  const [range, setRange] = useState<[Dayjs, Dayjs]>([dayjs().subtract(6, 'day'), dayjs()])
  const [action, setAction] = useState<AuditAction | undefined>()
  const [employeeId, setEmployeeId] = useState<number | undefined>()
  const [search, setSearch] = useState('')
  const from = range[0].format('YYYY-MM-DD')
  const to = range[1].format('YYYY-MM-DD')

  const log = useQuery({
    queryKey: ['audit-entries', from, to],
    queryFn: () => api.get<AuditEntry[]>('/audit-entries', { params: { from, to } }).then((r) => r.data),
  })
  const entries = log.data ?? []
  const people = [...new Map(entries.map((e) => [e.employeeId, e.employeeName])).entries()]
  const needle = search.trim().toLowerCase()
  const shown = entries.filter(
    (e) =>
      (!action || e.action === action) &&
      (!employeeId || e.employeeId === employeeId) &&
      (!needle || `${auditOrder(e)} ${e.subject}`.toLowerCase().includes(needle)),
  )

  return (
    <>
      <div className="page-title">
        <Typography.Title level={3}>Nhật ký thao tác</Typography.Title>
        <DatePicker.RangePicker
          value={range}
          format="DD/MM/YYYY"
          allowClear={false}
          onChange={(value) => value && value[0] && value[1] && setRange([value[0], value[1]])}
        />
      </div>
      <Flex gap={8} wrap style={{ marginBottom: 16 }}>
        <Select
          allowClear
          placeholder="Loại thao tác"
          style={{ width: 160 }}
          value={action}
          onChange={setAction}
          options={Object.entries(auditActionLabel).map(([value, label]) => ({ value, label }))}
        />
        <Select
          allowClear
          placeholder="Người làm"
          style={{ width: 200 }}
          value={employeeId}
          onChange={setEmployeeId}
          options={people.map(([value, label]) => ({ value, label }))}
        />
        <Input.Search allowClear placeholder="Bàn, số đơn, món, mã" style={{ width: 240 }} onChange={(e) => setSearch(e.target.value)} />
      </Flex>
      <Table<AuditEntry>
        size="small"
        rowKey="id"
        loading={log.isLoading}
        dataSource={shown}
        scroll={{ x: 900 }}
        columns={[
          { title: 'Thời gian', width: 110, render: (_, e) => time(e.createdAt) },
          { title: 'Người làm', dataIndex: 'employeeName' },
          { title: 'Thao tác', render: (_, e) => <Tag color={ACTION_COLOR[e.action]}>{auditActionLabel[e.action]}</Tag> },
          { title: 'Đơn', render: (_, e) => auditOrder(e) },
          { title: 'Món hoặc mã', dataIndex: 'subject' },
          { title: 'Thay đổi', render: (_, e) => auditChange(e) },
          { title: 'Số tiền', render: (_, e) => (e.amount === null ? '' : money(e.amount)) },
          { title: 'Lý do', dataIndex: 'reason' },
        ]}
      />
    </>
  )
}
