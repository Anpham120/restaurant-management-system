import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App, Button, Flex, Form, Input, InputNumber, Modal, Select, Typography } from 'antd'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import { api, errorMessage } from '@/shared/api/client'
import type { GoodsReceipt, InventoryItem, Supplier } from '@/shared/api/types'
import { money, moneyInputProps } from '@/shared/utils/format'
import { lineTotal, receiptTotal } from '../utils/receipt'

/** BR-37: at most 50 lines on one receipt. */
const MAX_LINES = 50

interface ReceiptValues {
  supplierId?: number
  note?: string
  lines?: { inventoryItemId?: number; quantity?: number | null; unitPrice?: number | null }[]
}

interface Props {
  open: boolean
  onClose: () => void
  items: InventoryItem[]
}

/** FR-09.6: a receipt is saved once and for good, so the total is shown before saving (BR-37). */
export default function ReceiptModal({ open, onClose, items }: Props) {
  return (
    <Modal title="Lập phiếu nhập" open={open} onCancel={onClose} footer={null} width={760} destroyOnHidden>
      <ReceiptForm onClose={onClose} items={items} />
    </Modal>
  )
}

/** Lives inside the modal, so every opening starts from an empty receipt. */
function ReceiptForm({ onClose, items }: Omit<Props, 'open'>) {
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  const [form] = Form.useForm<ReceiptValues>()
  const lines = Form.useWatch('lines', form)

  const suppliers = useQuery({ queryKey: ['suppliers'], queryFn: () => api.get<Supplier[]>('/suppliers').then((r) => r.data) })
  const active = (suppliers.data ?? []).filter((s) => s.active)
  const unitOf = (index: number) => items.find((i) => i.id === lines?.[index]?.inventoryItemId)?.unit

  const save = useMutation({
    mutationFn: (values: ReceiptValues) => api.post<GoodsReceipt>('/goods-receipts', values).then((r) => r.data),
    onSuccess: (receipt) => {
      message.success(`Đã lưu phiếu nhập #${receipt.id}: ${money(receipt.total)}`)
      for (const key of ['goods-receipts', 'inventory', 'inventory-movements']) queryClient.invalidateQueries({ queryKey: [key] })
      onClose()
    },
    onError: (e) => message.error(errorMessage(e)),
  })

  return (
    <Form form={form} layout="vertical" initialValues={{ lines: [{}] }} onFinish={(v) => save.mutate(v)}>
      {suppliers.isSuccess && active.length === 0 && (
        <Alert type="warning" showIcon style={{ marginBottom: 16 }} title='Chưa có nhà cung cấp đang giao dịch. Thêm ở thẻ "Nhà cung cấp" trước.' />
      )}
      <Form.Item name="supplierId" label="Nhà cung cấp" rules={[{ required: true, message: 'Chọn nhà cung cấp' }]}>
        <Select
          showSearch
          optionFilterProp="label"
          placeholder="Chọn nhà cung cấp"
          loading={suppliers.isLoading}
          options={active.map((s) => ({ value: s.id, label: s.name }))}
        />
      </Form.Item>
      <Form.List name="lines">
        {(fields, { add, remove }) => (
          <>
            {fields.map((field, index) => (
              <Flex key={field.key} gap={8} align="flex-start" wrap>
                <Form.Item name={[field.name, 'inventoryItemId']} rules={[{ required: true, message: 'Chọn nguyên liệu' }]} style={{ flex: '2 1 200px' }}>
                  <Select showSearch optionFilterProp="label" placeholder="Nguyên liệu" options={items.map((i) => ({ value: i.id, label: i.name }))} />
                </Form.Item>
                <Form.Item name={[field.name, 'quantity']} rules={[{ required: true, message: 'Nhập số lượng' }]} style={{ flex: '1 1 120px' }}>
                  <InputNumber min={0.001} max={999_999_999} step={0.5} placeholder="Số lượng" suffix={unitOf(index)} style={{ width: '100%' }} />
                </Form.Item>
                <Form.Item name={[field.name, 'unitPrice']} rules={[{ required: true, message: 'Nhập đơn giá' }]} style={{ flex: '1 1 150px' }}>
                  <InputNumber min={0} max={1_000_000_000} step={1_000} placeholder="Đơn giá" suffix="đ" style={{ width: '100%' }} {...moneyInputProps} />
                </Form.Item>
                <Typography.Text style={{ flex: '0 0 110px', lineHeight: '32px', textAlign: 'right' }}>
                  {money(lineTotal(lines?.[index]?.quantity, lines?.[index]?.unitPrice))}
                </Typography.Text>
                <Button aria-label="Bỏ dòng" icon={<DeleteOutlined />} onClick={() => remove(field.name)} disabled={fields.length === 1} />
              </Flex>
            ))}
            <Button icon={<PlusOutlined />} onClick={() => add()} disabled={fields.length >= MAX_LINES} style={{ marginBottom: 16 }}>
              Thêm dòng
            </Button>
          </>
        )}
      </Form.List>
      <Form.Item name="note" label="Ghi chú" rules={[{ max: 300 }]}>
        <Input placeholder="Số hoá đơn, người giao..." />
      </Form.Item>
      <Flex justify="space-between" align="center" gap={16} wrap>
        <Typography.Text strong>Tổng tiền phiếu: {money(receiptTotal(lines))}</Typography.Text>
        <Button type="primary" htmlType="submit" loading={save.isPending}>
          Lưu phiếu nhập
        </Button>
      </Flex>
      <Typography.Paragraph type="secondary" style={{ marginTop: 8, marginBottom: 0 }}>
        Phiếu đã lưu không sửa, không xoá được. Tồn kho và giá vốn cập nhật ngay khi lưu.
      </Typography.Paragraph>
    </Form>
  )
}
