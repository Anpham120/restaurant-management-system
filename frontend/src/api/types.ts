// Mirrors the backend DTOs (see docs-core/08-thiet-ke-he-thong.md).

export type Role = 'ADMIN' | 'MANAGER' | 'WAITER' | 'CHEF' | 'CASHIER'
export type OrderType = 'DINE_IN' | 'TAKEAWAY'
export type OrderStatus = 'OPEN' | 'PAID' | 'CANCELLED'
export type ItemStatus = 'PENDING' | 'WAITING' | 'COOKING' | 'READY' | 'SERVED' | 'CANCELLED'
export type PaymentMethod = 'CASH' | 'BANK_TRANSFER'
export type MovementType = 'IN' | 'OUT' | 'ADJUST'
export type PayType = 'HOURLY' | 'MONTHLY'

export interface Employee {
  id: number
  fullName: string
  username: string
  role: Role
  active: boolean
}

/** Admin view of an employee: profile and pay (BR-22). */
export interface EmployeeDetail extends Employee {
  phone: string | null
  hiredOn: string | null
  leftOn: string | null
  payType: PayType
  payRate: number
}

export interface LoginResponse {
  token: string
  user: Employee
}

export interface Category {
  id: number
  name: string
  sortOrder: number
}

export interface MenuItem {
  id: number
  categoryId: number
  categoryName: string
  name: string
  price: number
  description: string | null
  available: boolean
}

export interface MenuSection {
  categoryId: number
  categoryName: string
  items: MenuItem[]
}

export interface DiningTable {
  id: number
  name: string
  area: string | null
  seats: number
  qrToken: string
  qrUrl: string
  status: 'AVAILABLE' | 'OCCUPIED'
  openOrderId: number | null
  guestCount: number | null
  pendingCount: number
  readyCount: number
}

export interface OrderItem {
  id: number
  menuItemId: number
  itemName: string
  unitPrice: number
  quantity: number
  note: string | null
  status: ItemStatus
  source: 'STAFF' | 'GUEST'
  cancelReason: string | null
  createdAt: string
  sentAt: string | null
  updatedAt: string
}

export interface Order {
  id: number
  type: OrderType
  status: OrderStatus
  tableId: number | null
  tableName: string | null
  guestCount: number | null
  note: string | null
  openedAt: string
  closedAt: string | null
  total: number
  pendingCount: number
  unservedCount: number
  items: OrderItem[]
}

export interface KitchenItem {
  id: number
  orderId: number
  orderType: OrderType
  tableName: string | null
  itemName: string
  quantity: number
  note: string | null
  status: ItemStatus
  sentAt: string | null
  updatedAt: string
}

export interface ItemLine {
  menuItemId: number
  quantity: number
  note?: string
}

export interface Payment {
  id: number
  orderId: number
  method: PaymentMethod
  status: 'PENDING' | 'PAID' | 'CANCELLED'
  amount: number
  reference: string | null
  receivedAmount: number | null
  change: number | null
  confirmation: 'AUTO' | 'MANUAL' | null
  paidAt: string | null
}

export interface PaymentInstruction {
  paymentId: number
  orderId: number
  amount: number
  reference: string
  qrImageUrl: string
  bankCode: string
  bankAccountNo: string
  bankAccountName: string
}

export interface BankTransaction {
  id: number
  providerTxnId: string
  gateway: string | null
  amount: number
  content: string | null
  code: string | null
  matchStatus: 'MATCHED' | 'UNMATCHED' | 'IGNORED'
  note: string | null
  paymentId: number | null
  receivedAt: string
}

export interface GuestItem {
  id: number
  itemName: string
  unitPrice: number
  quantity: number
  note: string | null
  status: ItemStatus
  cancelReason: string | null
}

export interface GuestTable {
  tableName: string
  restaurantName: string
  order: {
    orderId: number
    items: GuestItem[]
    total: number
    pendingCount: number
    canPay: boolean
  } | null
}

export interface InventoryItem {
  id: number
  name: string
  unit: string
  quantity: number
  minQuantity: number
  lowStock: boolean
}

export interface StockMovement {
  id: number
  type: MovementType
  quantityChange: number
  quantityAfter: number
  note: string | null
  createdByName: string | null
  createdAt: string
}

export interface ReportSummary {
  from: string
  to: string
  revenue: number
  orderCount: number
  averagePerOrder: number
  byMethod: { method: PaymentMethod; amount: number; count: number }[]
  byDay: { date: string; amount: number; count: number }[]
  topItems: { itemName: string; quantity: number; amount: number }[]
}

export interface Settings {
  name: string
  address: string | null
  phone: string | null
  bankCode: string | null
  bankAccountNo: string | null
  bankAccountName: string | null
}

export interface WorkShift {
  id: number
  name: string
  startTime: string
  endTime: string
  active: boolean
}

export interface StaffMember {
  id: number
  fullName: string
  role: Role
}

export interface ShiftAssignment {
  id: number
  employeeId: number
  employeeName: string
  workShiftId: number
  shiftName: string
  workDate: string
  startTime: string
  endTime: string
}

export interface RealtimeMessage {
  type: 'ORDER_CHANGED' | 'PAYMENT_PAID' | 'MENU_CHANGED' | 'TABLES_CHANGED' | 'BANK_TRANSACTION'
  orderId: number | null
  tableId: number | null
}
