/** Kiểu dữ liệu khớp DTO backend (record Java → JSON camelCase). Tiền là number (BigDecimal). */

export type Role = 'MANAGER' | 'WAITER' | 'KITCHEN' | 'CASHIER';

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
  employeeId: number;
  username: string;
  roles: Role[];
  mustChangePassword: boolean;
}

export interface MeResponse {
  employeeId: number;
  username: string;
  fullName: string | null;
  roles: Role[];
}

export interface TableResponse {
  id: number;
  code: string;
  capacity: number;
  status: 'AVAILABLE' | 'OCCUPIED';
  active: boolean;
  currentSessionId: number | null;
  currentOrderId: number | null;
}

export interface OpenSessionResponse {
  sessionId: number;
  tableId: number;
  orderId: number;
}

export interface SessionResponse {
  sessionId: number;
  tableId: number;
  tableCode: string;
  status: string;
  openedAt: string;
  closedAt: string | null;
  orderId: number;
  orderStatus: string;
  invoiceId: number | null;
  invoiceStatus: InvoiceStatus | null;
  invoiceTotal: number | null;
}

export type ItemStatus = 'PENDING' | 'ACCEPTED' | 'COOKING' | 'READY' | 'SERVED' | 'REJECTED' | 'CANCELLED';

export interface OrderItem {
  id: number;
  orderId: number;
  batchId: number;
  dailyMenuItemId: number;
  itemName: string;
  quantity: number;
  unitPrice: number;
  status: ItemStatus;
  hasStockException: boolean;
  note: string | null;
  submittedAt: string;
  tableCode: string;
}

export interface OrderResponse {
  orderId: number;
  status: string;
  session: SessionResponse;
  items: OrderItem[];
}

export interface SubmitBatchResponse {
  batchId: number;
  replayed: boolean;
  warnings: { code: string; itemName: string; quantity: number; remaining: number }[];
  items: OrderItem[];
}

export interface AcceptItemResponse {
  result: 'ACCEPTED' | 'REJECTED';
  reason: string | null;
  item: OrderItem;
}

export interface DailyMenuItem {
  dailyMenuItemId: number;
  menuItemId: number;
  name: string;
  categoryName: string;
  price: number;
  quotaTotal: number | null;
  quotaUsed: number;
  remaining: number | null;
  paused: boolean;
  pauseReason: string | null;
  menuItemActive: boolean;
}

export interface DailyMenuResponse {
  id: number;
  businessDate: string;
  status: 'DRAFT' | 'PUBLISHED' | 'CLOSED';
  publishedAt: string | null;
  items: DailyMenuItem[];
}

export type InvoiceStatus = 'DRAFT' | 'PAYMENT_PENDING' | 'PAID' | 'UNCOLLECTED' | 'WAIVED';

export interface InvoiceResponse {
  invoiceId: number;
  orderId: number;
  status: InvoiceStatus;
  total: number;
  createdAt: string;
  uncollectedReason: string | null;
  waivedReason: string | null;
  lines: { orderItemId: number; itemName: string; quantity: number; unitPrice: number; lineTotal: number }[];
  paymentRequests: {
    id: number;
    method: 'CASH' | 'QR';
    status: string;
    amountDue: number;
    cashReceived: number | null;
    changeAmount: number | null;
    paymentReference: string | null;
    createdAt: string;
    cancelReason: string | null;
    qrImageUrl: string | null;
  }[];
  payment: { id: number; method: 'CASH' | 'QR'; amount: number; paidAt: string; businessDate: string } | null;
}

export interface PaymentResponse {
  invoiceId: number;
  method: 'CASH' | 'QR';
  status: 'PAID' | 'WAITING_TRANSFER';
  paymentId: number | null;
  paymentReference: string | null;
  amountDue: number;
  qrImageUrl: string | null;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
}

// ---------- thực đơn
export interface Category {
  id: number;
  name: string;
  sortOrder: number;
  active: boolean;
}

export interface MenuItem {
  id: number;
  categoryId: number;
  categoryName: string;
  name: string;
  price: number;
  imageUrl: string | null;
  active: boolean;
  currentRecipeVersion: number | null;
}

export interface Recipe {
  id: number;
  menuItemId: number;
  version: number;
  current: boolean;
  ingredients: { kitchenIngredientTypeId: number; typeName: string; baseUnit: string; quantityPerServing: number }[];
}

// ---------- kho
export interface Ingredient {
  id: number;
  name: string;
  baseUnit: string;
  minQuantity: number;
  expiryTracked: boolean;
  active: boolean;
}

export interface KitchenType {
  id: number;
  rawIngredientId: number;
  rawIngredientName: string;
  baseUnit: string;
  name: string;
  handlingType: 'DIRECT' | 'PREPARED';
  shelfLifeHours: number | null;
  minimumQuantity: number;
  active: boolean;
}

export type DocStatus = 'DRAFT' | 'CONFIRMED' | 'CANCELLED';

export interface ReceiptHeader {
  id: number;
  status: DocStatus;
  supplierName: string | null;
  note: string | null;
  requestKey: string;
  createdAt: string;
  createdById: number;
  receivedAt: string | null;
  confirmedById: number | null;
  confirmedAt: string | null;
}

export interface Receipt extends ReceiptHeader {
  totalAmount: number;
  lines: {
    id: number;
    ingredientId: number;
    ingredientName: string;
    baseUnit: string;
    inputQuantity: number;
    inputUnit: string;
    conversionFactor: number;
    baseQuantity: number;
    unitPricePerInputUnit: number;
    lineAmount: number;
    lotCode: string | null;
    expiryDate: string | null;
    inventoryBatchId: number | null;
  }[];
}

export type IssueStatus = 'DRAFT' | 'ISSUED' | 'RECEIVED' | 'CANCELLED';

export interface IssueHeader {
  id: number;
  status: IssueStatus;
  note: string | null;
  requestKey: string;
  createdAt: string;
  issuedById: number;
  issuedAt: string | null;
  receivedById: number | null;
  receivedAt: string | null;
  cancelReason: string | null;
  cancelledAt: string | null;
}

export interface Issue extends IssueHeader {
  lines: {
    id: number;
    inventoryBatchId: number;
    ingredientId: number;
    ingredientName: string;
    lotCode: string | null;
    quantity: number;
    fulfillmentMode: 'DIRECT' | 'PREPARE';
    wipRemainingQuantity: number | null;
    kitchenBatchId: number | null;
  }[];
}

export interface CountHeader {
  id: number;
  status: DocStatus;
  note: string | null;
  requestKey: string;
  createdAt: string;
  createdById: number;
  confirmedById: number | null;
  confirmedAt: string | null;
}

export interface Count extends CountHeader {
  lines: {
    id: number;
    rawBatchId: number | null;
    wipIssueItemId: number | null;
    kitchenBatchId: number | null;
    bookQuantity: number;
    actualQuantity: number;
    difference: number;
    reason: string | null;
  }[];
}

export interface PreparationHeader {
  id: number;
  status: DocStatus;
  stockIssueItemId: number;
  inputQuantity: number;
  lossReason: string | null;
  preparedById: number;
  preparedAt: string;
  requestKey: string;
}

export interface Preparation extends PreparationHeader {
  outputQuantity: number;
  lossQuantity: number;
  outputs: { id: number; kitchenIngredientTypeId: number; typeName: string; outputQuantity: number; usableUntil: string | null; kitchenBatchId: number | null }[];
}

export interface ReturnHeader {
  id: number;
  status: DocStatus;
  reason: string;
  requestedById: number;
  confirmedById: number | null;
  createdAt: string;
  confirmedAt: string | null;
  requestKey: string;
}

export interface StockReturn extends ReturnHeader {
  lines: { id: number; sourceIssueItemId: number; directKitchenBatchId: number | null; quantity: number }[];
}

export interface AdjustmentHeader {
  id: number;
  status: DocStatus;
  adjustmentType: 'DAMAGE' | 'EXPIRED' | 'QUALITY' | 'OTHER';
  reason: string;
  createdById: number;
  confirmedById: number | null;
  createdAt: string;
  confirmedAt: string | null;
  requestKey: string;
}

export interface Adjustment extends AdjustmentHeader {
  lines: { id: number; rawBatchId: number | null; wipIssueItemId: number | null; kitchenBatchId: number | null; quantityDelta: number; note: string | null }[];
}

export interface RawStockRow {
  ingredientId: number;
  ingredientName: string;
  baseUnit: string;
  usableQuantity: number;
  unusableQuantity: number;
  expiredQuantity: number;
  minQuantity: number;
  belowMinimum: boolean;
}

export interface KitchenStockRow {
  kitchenIngredientTypeId: number;
  typeName: string;
  baseUnit: string;
  usableOnHand: number;
  reservedQuantity: number;
  availableQuantity: number;
  unusableOrExpired: number;
  minimumQuantity: number;
}

export interface HandoverRow {
  stage: string;
  ingredientId: number;
  ingredientName: string;
  stockIssueId: number;
  stockIssueItemId: number;
  inventoryBatchId: number;
  quantity: number;
}

export interface UnusableLotRow {
  location: string;
  batchId: number;
  itemName: string;
  quantity: number;
  reason: string | null;
  expires: string | null;
}

export interface StockHistoryRow {
  movementId: number;
  createdAt: string;
  businessDate: string;
  category: string;
  movementType: string;
  location: string;
  ingredientId: number;
  ingredientName: string;
  quantityDelta: number;
  reference: string | null;
  reason: string | null;
}

export interface PreparationYieldRow {
  preparationId: number;
  businessDate: string;
  preparedAt: string;
  ingredientName: string;
  lotCode: string | null;
  preparedBy: string;
  inputQuantity: number;
  outputQuantity: number;
  lossQuantity: number;
  yieldRatio: number;
  lossRatio: number;
  lossReason: string | null;
}

export interface RawLot {
  inventoryBatchId: number;
  ingredientId: number;
  ingredientName: string;
  lotCode: string | null;
  expiryDate: string | null;
  receivedAt: string;
  quantityOnHand: number;
  usable: boolean;
}

export interface KitchenLot {
  kitchenBatchId: number;
  kitchenIngredientTypeId: number;
  typeName: string;
  quantityOnHand: number;
  reservedQuantity: number;
  usableUntil: string | null;
  usable: boolean;
  directIssueItemId: number | null;
}

// ---------- nhân sự
export interface Department {
  id: number;
  name: string;
}

export interface RoleInfo {
  id: number;
  roleCode: Role;
  roleName: string;
}

export interface Employee {
  id: number;
  employeeCode: string;
  fullName: string;
  gender: string | null;
  dateOfBirth: string | null;
  phone: string | null;
  email: string | null;
  address: string | null;
  hireDate: string | null;
  endDate: string | null;
  workStatus: 'ACTIVE' | 'INACTIVE' | 'LEFT';
  departmentId: number;
  departmentName: string;
  accountId: number | null;
  username: string | null;
  accountStatus: 'ACTIVE' | 'LOCKED' | 'DISABLED' | null;
  mustChangePassword: boolean | null;
  roles: Role[];
}

export interface Shift {
  id: number;
  name: string;
  startTime: string;
  endTime: string;
  breakMinutes: number;
  active: boolean;
}

export interface ShiftAssignment {
  id: number;
  employeeId: number;
  employeeName: string;
  shiftId: number;
  shiftName: string;
  workDate: string;
  scheduledStartAt: string;
  scheduledEndAt: string;
  scheduledBreakMinutes: number;
  status: 'SCHEDULED' | 'COMPLETED' | 'CANCELLED';
  note: string | null;
  cancelReason: string | null;
  cancelledAt: string | null;
  attendanceId: number | null;
}

export interface Attendance {
  id: number;
  shiftAssignmentId: number;
  employeeId: number;
  employeeName: string;
  workDate: string;
  status: 'PRESENT' | 'ABSENT';
  actualStartAt: string | null;
  actualEndAt: string | null;
  workedMinutes: number | null;
  note: string | null;
  confirmedById: number | null;
  confirmedAt: string | null;
}

export interface AttendanceAdjustment {
  id: number;
  oldStatus: string;
  newStatus: string;
  oldStartAt: string | null;
  newStartAt: string | null;
  oldEndAt: string | null;
  newEndAt: string | null;
  oldWorkedMinutes: number | null;
  newWorkedMinutes: number | null;
  reason: string;
  adjustedById: number;
  adjustedAt: string;
}

// ---------- tài chính
export interface Expense {
  id: number;
  businessDate: string;
  category: 'INGREDIENT' | 'SUPPLIES' | 'UTILITY' | 'RENT' | 'MAINTENANCE' | 'OTHER';
  description: string;
  amount: number;
  paymentMethod: 'CASH' | 'BANK_TRANSFER';
  fundSource: 'CASH_DRAWER' | 'OWNER' | 'BANK';
  stockReceiptId: number | null;
  recordedById: number;
  paidAt: string;
  createdAt: string;
  requestKey: string;
  status: 'PAID' | 'VOIDED';
  voidReason: string | null;
  voidedById: number | null;
  voidedAt: string | null;
}

export interface CashReconciliation {
  businessDate: string;
  openingCash: number | null;
  cashSales: number;
  cashExpenses: number;
  expectedCash: number | null;
  actualCash: number | null;
  difference: number | null;
  reason: string | null;
  reconVersion: number | null;
  reconciledById: number | null;
  stale: boolean;
  versions: {
    id: number;
    version: number;
    status: 'CURRENT' | 'SUPERSEDED';
    openingCash: number;
    cashSales: number;
    cashExpenses: number;
    expectedCash: number;
    actualCash: number;
    difference: number;
    reason: string | null;
    revisionReason: string | null;
    reconciledById: number;
    reconciledAt: string;
  }[];
}

export interface RevenueByDay {
  businessDate: string;
  cashAmount: number;
  qrAmount: number;
  totalAmount: number;
  paidInvoices: number;
}

export interface RevenueByItem {
  menuItemId: number;
  itemName: string;
  servings: number;
  revenue: number;
}

export interface UnpaidClosure {
  businessDate: string;
  status: 'UNCOLLECTED' | 'WAIVED';
  invoiceCount: number;
  amount: number;
}

export interface ExpenseByCategory {
  category: string;
  expenseCount: number;
  amount: number;
}
