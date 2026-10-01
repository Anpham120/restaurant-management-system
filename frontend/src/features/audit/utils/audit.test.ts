import { describe, expect, it } from 'vitest'
import type { AuditEntry } from '@/shared/api/types'
import { auditChange, auditOrder } from './audit'

const entry = (extra: Partial<AuditEntry>): AuditEntry => ({
  id: 1,
  action: 'ITEM_CANCELLED',
  employeeId: 2,
  employeeName: 'Nguyễn Văn Quản',
  orderId: 128,
  tableName: 'B05',
  subject: 'Nem rán x2',
  beforeValue: 'COOKING',
  afterValue: 'CANCELLED',
  amount: 130_000,
  reason: 'Khách đổi món',
  createdAt: '2026-10-01T12:00:00Z',
  ...extra,
})

describe('audit log lines (FR-16)', () => {
  it('turns raw item statuses into words', () => {
    expect(auditChange(entry({}))).toBe('Đang làm → Đã huỷ')
    expect(auditChange(entry({ beforeValue: 'PENDING' }))).toBe('Chờ xác nhận → Đã huỷ')
  })

  it('turns raw prices into money', () => {
    expect(auditChange(entry({ action: 'PRICE_CHANGED', orderId: null, beforeValue: '45000', afterValue: '50000' }))).toBe(
      '45.000 đ → 50.000 đ',
    )
  })

  it('shows nothing to change for a manual confirmation', () => {
    expect(auditChange(entry({ action: 'MANUAL_CONFIRMATION', beforeValue: null, afterValue: null }))).toBe('')
  })

  it('names the order by table, takeaway, or not at all', () => {
    expect(auditOrder(entry({}))).toBe('Bàn B05 · #128')
    expect(auditOrder(entry({ tableName: null, orderId: 12 }))).toBe('Mang về #12')
    expect(auditOrder(entry({ orderId: null, tableName: null }))).toBe('')
  })
})
