import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import type { Order, OrderItem, Payment, Settings } from '@/shared/api/types'
import BillSlip from './BillSlip'

const SETTINGS: Settings = {
  name: 'Khói Bếp',
  address: 'Phường Đống Đa, Hà Nội',
  phone: '0900000000',
  bankCode: null,
  bankAccountNo: null,
  bankAccountName: null,
  waitAlertMinutes: 15,
}

const item = (id: number, itemName: string, unitPrice: number, quantity: number, status: OrderItem['status']): OrderItem => ({
  id,
  menuItemId: id,
  itemName,
  unitPrice,
  quantity,
  note: null,
  status,
  source: 'STAFF',
  cancelReason: null,
  createdAt: '2026-10-01T11:05:00Z',
  sentAt: null,
  updatedAt: '2026-10-01T11:05:00Z',
})

// Local times, so the slip reads the same in any time zone.
const ORDER: Order = {
  id: 128,
  type: 'DINE_IN',
  status: 'OPEN',
  tableId: 5,
  tableName: 'B05',
  guestCount: 2,
  note: null,
  openedAt: new Date(2026, 9, 1, 18, 5).toISOString(),
  closedAt: null,
  total: 459_000,
  pendingCount: 1,
  unservedCount: 0,
  items: [
    item(1, 'Lẩu riêu cua bắp bò', 329_000, 1, 'SERVED'),
    item(2, 'Nem rán', 65_000, 2, 'READY'),
    item(3, 'Trà đá', 5_000, 2, 'CANCELLED'),
    item(4, 'Gỏi cuốn tôm thịt', 55_000, 1, 'PENDING'),
  ],
}
const PRINTED_AT = new Date(2026, 9, 1, 19, 42)

const PAID: Order = { ...ORDER, status: 'PAID', pendingCount: 0, items: ORDER.items.filter((i) => i.status !== 'PENDING') }
const payment = (method: Payment['method'], extra: Partial<Payment>): Payment => ({
  id: 9,
  orderId: 128,
  method,
  status: 'PAID',
  amount: 459_000,
  reference: null,
  receivedAmount: null,
  change: null,
  confirmation: null,
  paidAt: new Date(2026, 9, 1, 19, 50).toISOString(),
  ...extra,
})

describe('BillSlip', () => {
  it('bills only the dishes it charges for, with the total of the order (US-28 AC1, BR-33)', () => {
    render(<BillSlip order={ORDER} settings={SETTINGS} printedAt={PRINTED_AT} />)
    expect(screen.getByText('Khói Bếp')).toBeTruthy()
    expect(screen.getByText('ĐT 0900000000')).toBeTruthy()
    expect(screen.getByText('PHIẾU TẠM TÍNH')).toBeTruthy()
    expect(screen.getByText('Bàn B05 · Đơn #128 · 2 khách')).toBeTruthy()
    expect(screen.getByText('Vào 18:05 · In 19:42 01/10')).toBeTruthy()
    expect(screen.getByText('Lẩu riêu cua bắp bò')).toBeTruthy()
    expect(screen.getByText('2 x 65.000 đ')).toBeTruthy()
    expect(screen.getByText('130.000 đ')).toBeTruthy()
    expect(screen.queryByText('Trà đá')).toBeNull()
    expect(screen.queryByText('Gỏi cuốn tôm thịt')).toBeNull()
    expect(screen.getByText('459.000 đ')).toBeTruthy()
    expect(screen.getByText('Còn 1 món khách gửi chờ xác nhận, chưa tính.')).toBeTruthy()
    expect(screen.getByText('Phiếu này không thay hoá đơn GTGT.')).toBeTruthy()
  })

  it('shows the cash handed over and the change on a cash receipt (US-28 AC2)', () => {
    render(
      <BillSlip
        order={PAID}
        settings={SETTINGS}
        payment={payment('CASH', { receivedAmount: 500_000, change: 41_000 })}
        printedAt={PRINTED_AT}
      />,
    )
    expect(screen.getByText('PHIẾU THANH TOÁN')).toBeTruthy()
    expect(screen.getByText('Tiền mặt')).toBeTruthy()
    expect(screen.getByText('500.000 đ')).toBeTruthy()
    expect(screen.getByText('41.000 đ')).toBeTruthy()
    expect(screen.getByText('Đã trả lúc 19:50 01/10')).toBeTruthy()
    expect(screen.getByText('Cảm ơn quý khách!')).toBeTruthy()
    expect(screen.queryByText(/chờ xác nhận/)).toBeNull()
  })

  it('shows the transfer code on a transfer receipt (US-28 AC2)', () => {
    render(
      <BillSlip
        order={PAID}
        settings={SETTINGS}
        payment={payment('BANK_TRANSFER', { reference: 'KB7K3QX9MA', confirmation: 'AUTO' })}
        printedAt={PRINTED_AT}
      />,
    )
    expect(screen.getByText('Chuyển khoản')).toBeTruthy()
    expect(screen.getByText('Mã chuyển khoản: KB7K3QX9MA')).toBeTruthy()
    expect(screen.queryByText('Khách đưa')).toBeNull()
  })
})
