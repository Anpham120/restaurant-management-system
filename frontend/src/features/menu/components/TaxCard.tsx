import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Button, Card, DatePicker, Form, Input, Modal, Select, Table, Typography } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import dayjs, { type Dayjs } from 'dayjs'
import { api, errorMessage } from '@/shared/api/client'
import type { TaxCategory } from '@/shared/api/types'
import { upcomingRates, VAT_RATES } from '../utils/tax'

const rateOptions = VAT_RATES.map((rate) => ({ value: rate, label: `${rate}%` }))

/** FR-20.1, BR-45: tax categories of dishes and their VAT rates by day; a new rate starts today or later. */
export default function TaxCard() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  const [adding, setAdding] = useState(false)
  const [changing, setChanging] = useState<TaxCategory | null>(null)
  const taxes = useQuery({ queryKey: ['tax-categories'], queryFn: () => api.get<TaxCategory[]>('/tax-categories').then((r) => r.data) })
  const today = dayjs().format('YYYY-MM-DD')

  const saved = (text: string) => {
    message.success(text)
    queryClient.invalidateQueries({ queryKey: ['tax-categories'] })
  }
  const onError = (e: unknown) => message.error(errorMessage(e))
  const create = useMutation({
    mutationFn: (values: { name: string; rate: number }) => api.post('/tax-categories', values),
    onSuccess: () => {
      setAdding(false)
      saved('Đã thêm loại thuế')
    },
    onError,
  })
  const setRate = useMutation({
    mutationFn: ({ id, from, rate }: { id: number; from: string; rate: number }) =>
      api.put(`/tax-categories/${id}/rates/${from}`, { rate }),
    onSuccess: () => {
      setChanging(null)
      saved('Đã đặt thuế suất')
    },
    onError,
  })

  return (
    <Card
      title="Loại thuế"
      size="small"
      style={{ marginTop: 16 }}
      extra={<Button icon={<PlusOutlined />} onClick={() => setAdding(true)}>Thêm</Button>}
    >
      <Table<TaxCategory>
        size="small"
        rowKey="id"
        pagination={false}
        loading={taxes.isLoading}
        dataSource={taxes.data ?? []}
        scroll={{ x: 'max-content' }}
        columns={[
          { title: 'Tên', dataIndex: 'name' },
          {
            title: 'Thuế suất',
            render: (_, t) => (
              <>
                <div>{t.currentRate}%</div>
                {upcomingRates(t.rates, today).map((r) => (
                  <Typography.Text type="secondary" key={r.effectiveFrom} style={{ display: 'block' }}>
                    {r.rate}% từ {dayjs(r.effectiveFrom).format('DD/MM/YYYY')}
                  </Typography.Text>
                ))}
              </>
            ),
          },
          {
            title: '',
            render: (_, t) => (
              <Button size="small" aria-label="Đổi thuế suất" onClick={() => setChanging(t)}>
                Đổi
              </Button>
            ),
          },
        ]}
      />

      <Modal title="Thêm loại thuế" open={adding} onCancel={() => setAdding(false)} footer={null} destroyOnHidden>
        <Form layout="vertical" onFinish={(v: { name: string; rate: number }) => create.mutate(v)}>
          <Form.Item name="name" label="Tên" rules={[{ required: true, max: 50 }]}>
            <Input placeholder="Ví dụ: Rượu, bia" />
          </Form.Item>
          <Form.Item name="rate" label="Thuế suất, áp dụng từ hôm nay" rules={[{ required: true }]}>
            <Select options={rateOptions} />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={create.isPending}>Lưu</Button>
        </Form>
      </Modal>

      <Modal
        title={`Đổi thuế suất: ${changing?.name ?? ''}`}
        open={changing !== null}
        onCancel={() => setChanging(null)}
        footer={null}
        destroyOnHidden
      >
        {changing && (
          <Form
            layout="vertical"
            initialValues={{ rate: changing.currentRate, from: dayjs() }}
            onFinish={(v: { rate: number; from: Dayjs }) =>
              setRate.mutate({ id: changing.id, rate: v.rate, from: v.from.format('YYYY-MM-DD') })
            }
          >
            <Form.Item name="rate" label="Thuế suất mới" rules={[{ required: true }]}>
              <Select options={rateOptions} />
            </Form.Item>
            <Form.Item name="from" label="Áp dụng từ ngày" rules={[{ required: true }]}>
              <DatePicker
                format="DD/MM/YYYY"
                allowClear={false}
                disabledDate={(d) => d.isBefore(dayjs(), 'day')}
                style={{ width: '100%' }}
              />
            </Form.Item>
            <Typography.Paragraph type="secondary">
              Bill thanh toán từ ngày đó dùng thuế suất mới; hoá đơn đã lập giữ nguyên.
            </Typography.Paragraph>
            <Button type="primary" htmlType="submit" block loading={setRate.isPending}>Lưu</Button>
          </Form>
        )}
      </Modal>
    </Card>
  )
}
