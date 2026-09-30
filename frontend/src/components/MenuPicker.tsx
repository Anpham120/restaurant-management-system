import { useState } from 'react'
import { Button, Empty, Segmented, Tag, Typography } from 'antd'
import { MinusOutlined, PlusOutlined } from '@ant-design/icons'
import type { MenuItem, MenuSection } from '../api/types'
import { money } from '../utils/format'
import type { Cart } from './useCart'

/** Group a flat dish list (staff API) into categories like the guest API does. */
export function toSections(items: MenuItem[]): MenuSection[] {
  const sections = new Map<number, MenuSection>()
  for (const item of items) {
    if (!sections.has(item.categoryId)) {
      sections.set(item.categoryId, { categoryId: item.categoryId, categoryName: item.categoryName, items: [] })
    }
    sections.get(item.categoryId)!.items.push(item)
  }
  return [...sections.values()]
}

export default function MenuPicker({ sections, cart }: { sections: MenuSection[]; cart: Cart }) {
  const [categoryId, setCategoryId] = useState<number | null>(null)
  if (sections.length === 0) return <Empty description="Chưa có món" />
  const current = sections.find((s) => s.categoryId === categoryId) ?? sections[0]

  return (
    <div>
      <Segmented
        block
        value={current.categoryId}
        onChange={(value) => setCategoryId(Number(value))}
        options={sections.map((s) => ({ label: s.categoryName, value: s.categoryId }))}
        style={{ marginBottom: 8, overflowX: 'auto' }}
      />
      {current.items.map((item) => {
        const quantity = cart.quantities[item.id] ?? 0
        return (
          <div className="menu-row" key={item.id}>
            <div>
              <Typography.Text strong delete={!item.available}>
                {item.name}
              </Typography.Text>
              {!item.available && <Tag color="red" style={{ marginLeft: 8 }}>Hết món</Tag>}
              <div>
                <Typography.Text type="secondary">{money(item.price)}</Typography.Text>
              </div>
              {item.description && <Typography.Text type="secondary" style={{ fontSize: 12 }}>{item.description}</Typography.Text>}
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
              {quantity > 0 && (
                <>
                  <Button shape="circle" icon={<MinusOutlined />} onClick={() => cart.remove(item)} aria-label="Bớt" />
                  <Typography.Text strong>{quantity}</Typography.Text>
                </>
              )}
              <Button
                shape="circle"
                type="primary"
                icon={<PlusOutlined />}
                disabled={!item.available}
                onClick={() => cart.add(item)}
                aria-label={`Thêm ${item.name}`}
              />
            </div>
          </div>
        )
      })}
    </div>
  )
}
