import { Tag } from 'antd'
import type { ItemStatus } from '../api/types'
import { itemStatusColor, itemStatusLabel } from '../utils/format'

export default function StatusTag({ status }: { status: ItemStatus }) {
  return <Tag color={itemStatusColor[status]}>{itemStatusLabel[status]}</Tag>
}
