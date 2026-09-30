import dayjs from 'dayjs'
import type { ItemStatus, MovementType, OrderStatus, PayType, Role, ServiceRequestType } from '../api/types'

const vnd = new Intl.NumberFormat('vi-VN')

export function money(amount: number): string {
  return `${vnd.format(amount)} đ`
}

export function time(value: string | null): string {
  return value ? dayjs(value).format('HH:mm DD/MM') : ''
}

/** Whole minutes since the given time, for the kitchen wait counter. */
export function minutesSince(value: string | null, now: number = Date.now()): number {
  if (!value) return 0
  return Math.max(0, Math.floor((now - new Date(value).getTime()) / 60_000))
}

export const itemStatusLabel: Record<ItemStatus, string> = {
  PENDING: 'Chờ xác nhận',
  WAITING: 'Chờ làm',
  COOKING: 'Đang làm',
  READY: 'Xong',
  SERVED: 'Đã ra',
  CANCELLED: 'Đã huỷ',
}

export const itemStatusColor: Record<ItemStatus, string> = {
  PENDING: 'gold',
  WAITING: 'blue',
  COOKING: 'orange',
  READY: 'green',
  SERVED: 'default',
  CANCELLED: 'red',
}

export const orderStatusLabel: Record<OrderStatus, string> = {
  OPEN: 'Đang mở',
  PAID: 'Đã thanh toán',
  CANCELLED: 'Đã huỷ',
}

export const roleLabel: Record<Role, string> = {
  ADMIN: 'Quản trị',
  MANAGER: 'Quản lý',
  WAITER: 'Phục vụ',
  CHEF: 'Bếp',
  CASHIER: 'Thu ngân',
}

export const movementLabel: Record<MovementType, string> = {
  IN: 'Nhập kho',
  OUT: 'Xuất kho',
  ADJUST: 'Kiểm kê',
}

export const requestTypeLabel: Record<ServiceRequestType, string> = {
  CALL_STAFF: 'Gọi nhân viên',
  BILL: 'Xin tính tiền',
}

export const payTypeLabel: Record<PayType, string> = {
  HOURLY: 'Theo giờ',
  MONTHLY: 'Theo tháng',
}

/** "25.000 đ/giờ" or "8.000.000 đ/tháng". */
export function payText(type: PayType, rate: number): string {
  return `${money(rate)}/${type === 'HOURLY' ? 'giờ' : 'tháng'}`
}

/** InputNumber props that show thousands separators the way money() does. */
export const moneyInputProps = {
  formatter: (value?: string | number) => `${value ?? ''}`.replace(/\B(?=(\d{3})+(?!\d))/g, '.'),
  parser: (value?: string) => Number((value ?? '').replace(/\./g, '')),
}

/** Same hierarchy as the backend (BR-02): ADMIN > MANAGER > WAITER, CHEF, CASHIER. */
export function hasRole(userRole: Role | undefined, needed: Role): boolean {
  if (!userRole) return false
  if (userRole === needed || userRole === 'ADMIN') return true
  return userRole === 'MANAGER' && needed !== 'ADMIN'
}

/** First screen after login for each role. */
export function homePath(role: Role): string {
  switch (role) {
    case 'WAITER':
      return '/tables'
    case 'CHEF':
      return '/kitchen'
    case 'CASHIER':
      return '/cashier'
    default:
      return '/admin/reports'
  }
}

/** Cash shortcuts for the cashier: exact amount, then rounded up to common notes. */
export function cashSuggestions(total: number): number[] {
  const steps = [10_000, 50_000, 100_000, 500_000]
  const values = new Set<number>([total])
  for (const step of steps) values.add(Math.ceil(total / step) * step)
  return [...values].filter((v) => v > 0).sort((a, b) => a - b).slice(0, 4)
}
