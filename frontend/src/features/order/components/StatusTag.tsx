import { Tag } from 'antd'
import type { ItemStatus } from '@/shared/api/types'
import { itemStatusColor, itemStatusLabel } from '@/shared/utils/format'

export default function StatusTag({ status }: { status: ItemStatus }) {
  return <Tag color={itemStatusColor[status]}>{itemStatusLabel[status]}</Tag>
}
