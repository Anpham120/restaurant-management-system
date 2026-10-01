import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { App, Button, Descriptions, Divider, Form, Input, Modal } from 'antd'
import dayjs from 'dayjs'
import { api, errorMessage } from '@/shared/api/client'
import type { Customer, Order } from '@/shared/api/types'
import { money } from '@/shared/utils/format'
import ConsentControls from './ConsentControls'
import { normalizePhone } from '../utils/customer'

interface Props {
  order: Order
  onClose: () => void
}

/** FR-19.2: the cashier asks the guest's phone number and sees how often they came; a new number is a new guest (BR-44). */
export default function AttachCustomerModal({ order, onClose }: Props) {
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  const [customer, setCustomer] = useState<Customer | null>(null)

  const attach = useMutation({
    mutationFn: (values: { phone: string; name?: string }) =>
      api.post<Customer>(`/orders/${order.id}/customer`, values).then((r) => r.data),
    onSuccess: (c) => {
      setCustomer(c)
      queryClient.invalidateQueries({ queryKey: ['order', order.id] })
      queryClient.invalidateQueries({ queryKey: ['orders'] })
    },
    onError: (e) => message.error(errorMessage(e)),
  })

  return (
    <Modal title="Khách hàng" open onCancel={onClose} footer={null} destroyOnHidden>
      <Form layout="vertical" initialValues={{ phone: order.customerPhone ?? '' }} onFinish={attach.mutate}>
        <Form.Item
          name="phone"
          label="Số điện thoại"
          rules={[
            {
              validator: (_, value?: string) =>
                normalizePhone(value ?? '') ? Promise.resolve() : Promise.reject(new Error('Số điện thoại phải có 10 số, bắt đầu bằng 0')),
            },
          ]}
        >
          <Input inputMode="tel" maxLength={20} autoFocus />
        </Form.Item>
        <Form.Item name="name" label="Tên, khi là khách mới">
          <Input maxLength={100} />
        </Form.Item>
        <Button type="primary" htmlType="submit" loading={attach.isPending} block>
          Gắn vào đơn
        </Button>
      </Form>
      {customer && (
        <>
          <Divider />
          <Descriptions
            size="small"
            column={1}
            items={[
              { key: 'who', label: 'Khách', children: `${customer.name ?? 'Chưa có tên'}, ${customer.phone}` },
              { key: 'visits', label: 'Số lần ghé', children: customer.visits },
              { key: 'spent', label: 'Tổng chi', children: money(customer.spent) },
              {
                key: 'last',
                label: 'Lần ghé gần nhất',
                children: customer.lastVisitAt ? dayjs(customer.lastVisitAt).format('DD/MM/YYYY') : 'Lần đầu',
              },
            ]}
          />
          <ConsentControls key={customer.id} customer={customer} onChanged={setCustomer} />
        </>
      )}
    </Modal>
  )
}
