import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Button, Flex, Form, InputNumber, Modal, Select, Typography } from 'antd'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import { api, errorMessage } from '@/shared/api/client'
import type { InventoryItem, MenuItem, Recipe } from '@/shared/api/types'

/** BR-38: at most 30 ingredients in one recipe. */
const MAX_LINES = 30

interface RecipeValues {
  lines?: { inventoryItemId?: number; quantity?: number | null }[]
}

interface Props {
  dish: MenuItem | null
  recipe: Recipe | undefined
  onClose: () => void
}

/** FR-09.8: what one portion of a dish takes from stock when it goes to the kitchen (BR-38). */
export default function RecipeModal({ dish, recipe, onClose }: Props) {
  return (
    <Modal title={dish ? `Định lượng: ${dish.name}` : ''} open={dish !== null} onCancel={onClose} footer={null} width={640} destroyOnHidden>
      {dish && <RecipeForm dish={dish} recipe={recipe} onClose={onClose} />}
    </Modal>
  )
}

/** Lives inside the modal, so every opening starts from the saved recipe. */
function RecipeForm({ dish, recipe, onClose }: { dish: MenuItem; recipe: Recipe | undefined; onClose: () => void }) {
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  const [form] = Form.useForm<RecipeValues>()
  const lines = Form.useWatch('lines', form)

  const items = useQuery({ queryKey: ['inventory'], queryFn: () => api.get<InventoryItem[]>('/inventory-items').then((r) => r.data) })
  const unitOf = (index: number) => items.data?.find((i) => i.id === lines?.[index]?.inventoryItemId)?.unit

  const save = useMutation({
    mutationFn: (values: RecipeValues) => api.put(`/menu-items/${dish.id}/recipe`, { lines: values.lines ?? [] }),
    onSuccess: () => {
      message.success('Đã lưu định lượng')
      queryClient.invalidateQueries({ queryKey: ['recipes'] })
      onClose()
    },
    onError: (e) => message.error(errorMessage(e)),
  })

  return (
    <Form
      form={form}
      layout="vertical"
      initialValues={{ lines: recipe?.lines.map((l) => ({ inventoryItemId: l.inventoryItemId, quantity: l.quantity })) ?? [] }}
      onFinish={(v) => save.mutate(v)}
    >
      <Typography.Paragraph type="secondary">
        Lượng cho một phần, theo đơn vị của nguyên liệu. Món vào bếp thì kho tự trừ; sửa định lượng không đổi các lần đã trừ.
      </Typography.Paragraph>
      <Form.List name="lines">
        {(fields, { add, remove }) => (
          <>
            {fields.length === 0 && <Typography.Paragraph>Chưa có định lượng: món này không trừ kho.</Typography.Paragraph>}
            {fields.map((field, index) => (
              <Flex key={field.key} gap={8} align="flex-start" wrap>
                <Form.Item name={[field.name, 'inventoryItemId']} rules={[{ required: true, message: 'Chọn nguyên liệu' }]} style={{ flex: '2 1 220px' }}>
                  <Select
                    showSearch
                    optionFilterProp="label"
                    placeholder="Nguyên liệu"
                    loading={items.isLoading}
                    options={(items.data ?? []).map((i) => ({ value: i.id, label: i.name }))}
                  />
                </Form.Item>
                <Form.Item name={[field.name, 'quantity']} rules={[{ required: true, message: 'Nhập lượng' }]} style={{ flex: '1 1 140px' }}>
                  <InputNumber min={0.001} max={999_999_999} step={0.05} placeholder="Lượng" suffix={unitOf(index)} style={{ width: '100%' }} />
                </Form.Item>
                <Button aria-label="Bỏ nguyên liệu" icon={<DeleteOutlined />} onClick={() => remove(field.name)} />
              </Flex>
            ))}
            <Button icon={<PlusOutlined />} onClick={() => add()} disabled={fields.length >= MAX_LINES} style={{ marginBottom: 16 }}>
              Thêm nguyên liệu
            </Button>
          </>
        )}
      </Form.List>
      <Button type="primary" htmlType="submit" block loading={save.isPending}>
        Lưu định lượng
      </Button>
    </Form>
  )
}
