// Mirrors the backend DTOs (see docs-core/08-thiet-ke-he-thong.md).

export type Role = 'ADMIN' | 'MANAGER' | 'WAITER' | 'CHEF' | 'CASHIER'
export type OrderType = 'DINE_IN' | 'TAKEAWAY'
export type OrderStatus = 'OPEN' | 'PAID' | 'CANCELLED'
export type ItemStatus = 'PENDING' | 'WAITING' | 'COOKING' | 'READY' | 'SERVED' | 'CANCELLED'
export type PaymentMethod = 'CASH' | 'BANK_TRANSFER'
export type MovementType = 'IN' | 'OUT' | 'ADJUST' | 'SALE'
export type PayType = 'HOURLY' | 'MONTHLY'
export type LeaveType = 'PAID' | 'UNPAID'
export type LeaveStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'
export type PayrollStatus = 'DRAFT' | 'FINALIZED'

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
  /** FR-04.5: the tables of the open order, "B05 + B06", when it holds more than this one. */
  groupLabel: string | null
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
  /** The main table; null for takeaway. */
  tableId: number | null
  /** BR-36: every table the order holds, the main one first. */
  tableIds: number[]
  /** "B05 + B06" when tables are put together. */
  tableName: string | null
  guestCount: number | null
  note: string | null
  openedAt: string
  closedAt: string | null
  /** BR-12: dishes billed, before discounts. */
  subtotal: number
  /** BR-35: what the adjustments in effect take off. */
  discountTotal: number
  /** What the guest pays: subtotal minus discounts. */
  total: number
  pendingCount: number
  unservedCount: number
  /** BR-13: discounts waiting for a manager block payment. */
  pendingAdjustmentCount: number
  items: OrderItem[]
  adjustments: Adjustment[]
}

export type AdjustmentType = 'DISCOUNT' | 'COMP'
export type AdjustmentReason = 'WAIT' | 'FOOD_QUALITY' | 'STAFF_ERROR' | 'PROMOTION' | 'OTHER'
export type AdjustmentStatus = 'PENDING' | 'APPLIED' | 'REJECTED' | 'CANCELLED'

/** FR-08.10: a discount on the bill, or a dish line given free (orderItemId, itemName). */
export interface Adjustment {
  id: number
  orderId: number
  tableName: string | null
  type: AdjustmentType
  orderItemId: number | null
  itemName: string | null
  amount: number
  reason: AdjustmentReason
  note: string | null
  status: AdjustmentStatus
  createdByName: string
  createdAt: string
  decidedByName: string | null
  decidedAt: string | null
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

/** BR-34: the sensitive actions in the audit log. */
export type AuditAction = 'ITEM_CANCELLED' | 'MANUAL_CONFIRMATION' | 'PRICE_CHANGED' | 'DISCOUNT_GIVEN'

/** FR-16: one line of the audit log; before and after are raw, an item status code or a price in VND. */
export interface AuditEntry {
  id: number
  action: AuditAction
  employeeId: number
  employeeName: string
  orderId: number | null
  tableName: string | null
  subject: string
  beforeValue: string | null
  afterValue: string | null
  amount: number | null
  reason: string | null
  createdAt: string
}

/** BR-32: failing once SePay deliveries failed 3 times in a row; since is when that run of failures began. */
export interface WebhookStatus {
  failing: boolean
  failures: number
  since: string | null
  lastError: string | null
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
    /** FR-08.10: what discounts take off; total is what is left to pay. */
    discountTotal: number
    total: number
    pendingCount: number
    canPay: boolean
  } | null
  /** Calls of this table that no waiter has taken yet (FR-06.6). */
  openRequests: ServiceRequestType[]
}

export type ServiceRequestType = 'CALL_STAFF' | 'BILL'

export interface ServiceRequest {
  id: number
  tableId: number
  tableName: string
  type: ServiceRequestType
  createdAt: string
}

export interface InventoryItem {
  id: number
  name: string
  unit: string
  quantity: number
  minQuantity: number
  lowStock: boolean
  /** VND for one unit, the weighted average of receipts; null before the first receipt (BR-37). */
  unitCost: number | null
}

/** FR-09.5: never deleted; one that is not active cannot be picked for a new receipt. */
export interface Supplier {
  id: number
  name: string
  phone: string | null
  address: string | null
  taxCode: string | null
  note: string | null
  active: boolean
}

export interface ReceiptLine {
  id: number
  inventoryItemId: number
  itemName: string
  unit: string
  quantity: number
  unitPrice: number
  lineTotal: number
}

/** FR-09.6: saved once, never edited or deleted (BR-37). */
export interface GoodsReceipt {
  id: number
  supplierId: number
  supplierName: string
  note: string | null
  createdByName: string
  createdAt: string
  total: number
  lines: ReceiptLine[]
}

/** FR-09.8: how much of an ingredient one portion of a dish takes, in the ingredient's unit (BR-38). */
export interface RecipeLine {
  inventoryItemId: number
  itemName: string
  unit: string
  quantity: number
}

export interface Recipe {
  menuItemId: number
  lines: RecipeLine[]
}

/** FR-09.10, in the ingredient's unit: used and removed left the stock; adjusted is negative when a count found less. */
export interface StockUsage {
  inventoryItemId: number
  name: string
  unit: string
  used: number
  removed: number
  adjusted: number
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
  /** A dish this many minutes in the kitchen is shown in red (BR-28). */
  waitAlertMinutes: number
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

export interface AttendanceRecord {
  id: number
  employeeId: number
  employeeName: string
  shiftAssignmentId: number | null
  shiftName: string | null
  workDate: string
  shiftStart: string | null
  shiftEnd: string | null
  checkInAt: string
  checkOutAt: string | null
  lateMinutes: number
  earlyMinutes: number
  workedMinutes: number | null
  editReason: string | null
  editedByName: string | null
  editedAt: string | null
}

export interface ClockStatus {
  current: AttendanceRecord | null
  todayShifts: { assignmentId: number; shiftName: string; startTime: string; endTime: string; done: boolean }[]
}

export interface LeaveRequest {
  id: number
  employeeId: number
  employeeName: string
  fromDate: string
  toDate: string
  days: number
  type: LeaveType
  reason: string
  status: LeaveStatus
  decidedByName: string | null
  decisionNote: string | null
  createdAt: string
  decidedAt: string | null
}

export interface PayAdjustment {
  id: number
  amount: number
  reason: string
  createdAt: string
}

export interface Payslip {
  id: number
  period: string
  status: PayrollStatus
  employeeId: number
  employeeName: string
  role: Role
  payType: PayType
  payRate: number
  workedMinutes: number
  workDays: number
  paidLeaveDays: number
  baseAmount: number
  adjustmentAmount: number
  netAmount: number
  adjustments: PayAdjustment[]
}

export interface PayrollSummary {
  id: number
  period: string
  status: PayrollStatus
  standardDays: number
  payslipCount: number
  totalNet: number
  finalizedAt: string | null
}

export interface PayrollDetail extends PayrollSummary {
  finalizedByName: string | null
  payslips: Payslip[]
}

export interface MyPayslip {
  id: number
  period: string
  netAmount: number
}

/**
 * Why staff screens ring (FR-07.5, FR-06.6): dishes reached the kitchen, a guest sent dishes, a dish is ready,
 * a guest called a waiter or asked for the bill.
 */
export type StaffAlert = 'NEW_DISHES' | 'GUEST_DISHES' | 'DISH_READY' | 'SERVICE_REQUEST'

export interface RealtimeMessage {
  type: 'ORDER_CHANGED' | 'PAYMENT_PAID' | 'MENU_CHANGED' | 'TABLES_CHANGED' | 'BANK_TRANSACTION' | 'REQUESTS_CHANGED' | 'WEBHOOK_STATUS' | 'ADJUSTMENTS_CHANGED'
  orderId: number | null
  tableId: number | null
  alert: StaffAlert | null
}
