import { useMutation, useQueryClient } from '@tanstack/react-query'
import { App, Button, Form, Input, Modal, Typography } from 'antd'
import { api, errorMessage } from '@/shared/api/client'
import type { EInvoice, EInvoiceDetail } from '@/shared/api/types'
import { isInvoiceNo, isSymbol } from '../utils/einvoice'

interface Props {
  invoice: EInvoice
  /** The symbol to start from: the one last recorded on the screen. */
  symbol?: string
  onClose: () => void
}

type Issued = { symbol: string; number: string }

/** FR-20.4, BR-46: the symbol and number the invoice was issued under on MISA; recording them again corrects them. */
export default function NumberModal({ invoice, symbol, onClose }: Props) {
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  const save = useMutation({
    mutationFn: (values: Issued) => api.put<EInvoiceDetail>(`/einvoices/${invoice.id}/number`, values).then((r) => r.data),
    onSuccess: () => {
      message.success('Đã ghi số hoá đơn')
      queryClient.invalidateQueries({ queryKey: ['einvoices'] })
      queryClient.invalidateQueries({ queryKey: ['einvoice', invoice.id] })
      onClose()
    },
    onError: (e) => message.error(errorMessage(e)),
  })

  return (
    <Modal title={`Số hoá đơn của đơn #${invoice.orderId}`} open onCancel={onClose} footer={null} destroyOnHidden>
      <Form<Issued>
        layout="vertical"
        initialValues={{ symbol: invoice.invoiceSymbol ?? symbol ?? '', number: invoice.invoiceNo ?? '' }}
        onFinish={(v) => save.mutate(v)}
      >
        <Typography.Paragraph type="secondary">Ghi đúng ký hiệu và số của hoá đơn đã phát hành trên MISA.</Typography.Paragraph>
        <Form.Item
          name="symbol"
          label="Ký hiệu"
          normalize={(v?: string) => v?.toUpperCase()}
          rules={[
            { required: true, message: 'Nhập ký hiệu' },
            {
              validator: (_, v?: string) =>
                !v || isSymbol(v) ? Promise.resolve() : Promise.reject(new Error('Ký hiệu có 7 ký tự, ví dụ 1C26MKB')),
            },
          ]}
        >
          <Input maxLength={7} placeholder="1C26MKB" />
        </Form.Item>
        <Form.Item
          name="number"
          label="Số hoá đơn"
          rules={[
            { required: true, message: 'Nhập số hoá đơn' },
            {
              validator: (_, v?: string) =>
                !v || isInvoiceNo(v) ? Promise.resolve() : Promise.reject(new Error('Từ 1 đến 8 chữ số')),
            },
          ]}
        >
          <Input maxLength={8} inputMode="numeric" />
        </Form.Item>
        <Button type="primary" htmlType="submit" block loading={save.isPending}>Lưu</Button>
      </Form>
    </Modal>
  )
}
