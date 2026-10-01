import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Input, Table, Tag, Typography } from 'antd'
import dayjs from 'dayjs'
import { api } from '@/shared/api/client'
import type { Customer } from '@/shared/api/types'
import { money } from '@/shared/utils/format'
import CustomerDrawer from '../components/CustomerDrawer'
import { consentColor, consentLabel, consentState } from '../utils/customer'

/** FR-19: guests by phone number or name, the newest first, with their visits and what they agreed to hear (BR-44). */
export default function CustomersPage() {
  const [search, setSearch] = useState('')
  const [openId, setOpenId] = useState<number | null>(null)
  const customers = useQuery({
    queryKey: ['customers', search],
    queryFn: () => api.get<Customer[]>('/customers', { params: { q: search } }).then((r) => r.data),
  })

  return (
    <>
      <div className="page-title">
        <Typography.Title level={3}>Khách hàng</Typography.Title>
        <Input.Search allowClear placeholder="Số điện thoại hoặc tên" style={{ width: 260 }} onSearch={(q) => setSearch(q.trim())} />
      </div>
      <Table<Customer>
        size="small"
        rowKey="id"
        loading={customers.isLoading}
        dataSource={customers.data ?? []}
        pagination={false}
        scroll={{ x: 'max-content' }}
        onRow={(c) => ({ onClick: () => setOpenId(c.id), style: { cursor: 'pointer' } })}
        columns={[
          {
            title: 'Khách',
            render: (_, c) => (
              <>
                <div>{c.name ?? 'Chưa có tên'}</div>
                <Typography.Text type="secondary">{c.phone}</Typography.Text>
              </>
            ),
          },
          { title: 'Số lần ghé', dataIndex: 'visits' },
          { title: 'Tổng chi', render: (_, c) => money(c.spent) },
          { title: 'Lần ghé gần nhất', render: (_, c) => (c.lastVisitAt ? dayjs(c.lastVisitAt).format('DD/MM/YYYY') : '') },
          {
            title: 'Nhận tin',
            render: (_, c) => {
              const state = consentState(c)
              return <Tag color={consentColor[state]}>{consentLabel[state]}</Tag>
            },
          },
          { title: 'Ghi chú', dataIndex: 'note' },
        ]}
      />
      <CustomerDrawer id={openId} onClose={() => setOpenId(null)} />
    </>
  )
}
