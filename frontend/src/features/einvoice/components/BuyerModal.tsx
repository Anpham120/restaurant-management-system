import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App, Button, Descriptions, Form, Input, Modal, Spin, Typography } from 'antd'
import { api, errorMessage } from '@/shared/api/client'
import type { EInvoiceDetail } from '@/shared/api/types'
import { isTaxCode } from '../utils/einvoice'

interface Props {
  orderId: number
  onClose: () => void
}

type Buyer = { name?: string; taxCode?: string; address?: string; email?: string }

const filled = (value: unknown) => `${value ?? ''}`.trim() !== ''

/** FR-20.3, BR-46: the buyer on the e-invoice of a paid bill; everything blank is a walk-in guest. */
export default function BuyerModal({ orderId, onClose }: Props) {
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  const detail = useQuery({
    queryKey: ['order-einvoice', orderId],
    queryFn: () => api.get<EInvoiceDetail>(`/orders/${orderId}/einvoice`).then((r) => r.data),
  })
  const invoice = detail.data?.invoice

  const save = useMutation({
    mutationFn: (values: Buyer) => api.put<EInvoiceDetail>(`/einvoices/${invoice?.id}/buyer`, values).then((r) => r.data),
    onSuccess: (saved) => {
      message.success(saved.invoice.buyerName ? 'Đã lưu người mua' : 'Hoá đơn ghi khách lẻ')
      queryClient.setQueryData(['order-einvoice', orderId], saved)
      queryClient.invalidateQueries({ queryKey: ['einvoices'] })
      queryClient.invalidateQueries({ queryKey: ['einvoice', saved.invoice.id] })
      onClose()
    },
    onError: (e) => message.error(errorMessage(e)),
  })

  return (
    <Modal title="Người mua trên hoá đơn điện tử" open onCancel={onClose} footer={null} destroyOnHidden>
      {detail.isLoading && <Spin />}
      {detail.isError && <Alert type="error" showIcon title={errorMessage(detail.error)} />}
      {invoice?.status === 'ISSUED' && (
        <>
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 12 }}
            title={`Hoá đơn đã có số ${invoice.invoiceSymbol} ${invoice.invoiceNo}, sửa người mua trên MISA.`}
          />
          <Descriptions
            size="small"
            column={1}
            items={[
              { key: 'name', label: 'Người mua', children: invoice.buyerName ?? 'Khách lẻ' },
              { key: 'tax', label: 'Mã số thuế', children: invoice.buyerTaxCode ?? '' },
              { key: 'address', label: 'Địa chỉ', children: invoice.buyerAddress ?? '' },
              { key: 'email', label: 'Email', children: invoice.buyerEmail ?? '' },
            ]}
          />
        </>
      )}
      {invoice && invoice.status !== 'ISSUED' && (
        <Form<Buyer>
          layout="vertical"
          initialValues={{
            name: invoice.buyerName ?? '',
            taxCode: invoice.buyerTaxCode ?? '',
            address: invoice.buyerAddress ?? '',
            email: invoice.buyerEmail ?? '',
          }}
          onFinish={(v) => save.mutate(v)}
        >
          <Typography.Paragraph type="secondary">Để trống cả bốn ô thì hoá đơn ghi khách lẻ.</Typography.Paragraph>
          <Form.Item
            name="name"
            label="Tên người mua hoặc công ty"
            dependencies={['taxCode', 'address', 'email']}
            rules={[
              { max: 200 },
              ({ getFieldValue }) => ({
                validator: (_, value?: string) =>
                  !filled(value) && ['taxCode', 'address', 'email'].some((f) => filled(getFieldValue(f)))
                    ? Promise.reject(new Error('Nhập tên người mua'))
                    : Promise.resolve(),
              }),
            ]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            name="taxCode"
            label="Mã số thuế"
            rules={[
              {
                validator: (_, value?: string) =>
                  !filled(value) || isTaxCode(value ?? '')
                    ? Promise.resolve()
                    : Promise.reject(new Error('10 số, 10 số và 3 số chi nhánh, hoặc 12 số')),
              },
            ]}
          >
            <Input maxLength={14} inputMode="numeric" />
          </Form.Item>
          <Form.Item name="address" label="Địa chỉ" rules={[{ max: 300 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="email" label="Email nhận hoá đơn" rules={[{ type: 'email', message: 'Email không đúng dạng' }, { max: 100 }]}>
            <Input />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={save.isPending}>Lưu</Button>
        </Form>
      )}
    </Modal>
  )
}
