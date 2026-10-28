const money = new Intl.NumberFormat('vi-VN');

/** 45000 → "45.000 ₫" */
export function vnd(n: number | null | undefined): string {
  return n == null ? '—' : `${money.format(n)}\u00a0₫`; // không ngắt dòng trước ₫
}

/** Số phút đã trôi qua kể từ một thời điểm ISO. */
export function minutesSince(iso: string, now = Date.now()): number {
  return Math.max(0, Math.floor((now - new Date(iso).getTime()) / 60000));
}

export function elapsed(iso: string, now = Date.now()): string {
  const m = minutesSince(iso, now);
  if (m < 60) return `${m} phút`;
  return `${Math.floor(m / 60)} giờ ${m % 60} phút`;
}

/** Giờ luôn theo giờ nhà hàng (Việt Nam), không theo múi giờ của máy đang mở trang. */
export function clock(iso: string): string {
  return new Date(iso).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit', timeZone: 'Asia/Ho_Chi_Minh' });
}

export const itemStatusLabel: Record<string, string> = {
  PENDING: 'Chờ bếp nhận',
  ACCEPTED: 'Bếp đã nhận',
  COOKING: 'Đang nấu',
  READY: 'Xong, chờ mang ra',
  SERVED: 'Đã phục vụ',
  REJECTED: 'Bếp từ chối',
  CANCELLED: 'Đã hủy',
};

export const invoiceStatusLabel: Record<string, string> = {
  DRAFT: 'Hóa đơn nháp',
  PAYMENT_PENDING: 'Chờ thanh toán',
  PAID: 'Đã thanh toán',
  UNCOLLECTED: 'Khách bỏ về',
  WAIVED: 'Miễn thu',
};

export const roleLabel: Record<string, string> = {
  MANAGER: 'Quản lý',
  WAITER: 'Phục vụ',
  KITCHEN: 'Bếp',
  CASHIER: 'Thu ngân',
};

const qty = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 3 });

/** Số lượng kho: tối đa 3 chữ số thập phân. */
export function amount(n: number | null | undefined, unit?: string): string {
  if (n == null) return '—';
  return unit ? `${qty.format(n)}\u00a0${unit}` : qty.format(n);
}

const VN = 'Asia/Ho_Chi_Minh';

export function dateTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('vi-VN', { timeZone: VN, hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit', year: 'numeric' });
}

/** "2026-10-27" → "27/10/2026" */
export function day(isoDate: string | null | undefined): string {
  if (!isoDate) return '—';
  const [y, m, d] = isoDate.split('-');
  return `${d}/${m}/${y}`;
}

/** Ngày kinh doanh hiện tại: giờ Việt Nam lùi 4 tiếng (ca đêm sau 0 giờ vẫn thuộc ngày hôm trước). */
export function businessToday(now = new Date()): string {
  return new Date(now.getTime() - 4 * 3600_000).toLocaleDateString('en-CA', { timeZone: VN });
}

export function addDays(isoDate: string, n: number): string {
  const d = new Date(isoDate + 'T00:00:00Z');
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
}

/** Thứ Hai của tuần chứa ngày đã cho. */
export function weekStart(isoDate: string): string {
  const d = new Date(isoDate + 'T00:00:00Z');
  return addDays(isoDate, -((d.getUTCDay() + 6) % 7));
}

export function weekdayLabel(isoDate: string): string {
  return new Date(isoDate + 'T00:00:00Z').toLocaleDateString('vi-VN', { weekday: 'short', day: '2-digit', month: '2-digit', timeZone: 'UTC' });
}

/** Instant ISO → giá trị cho <input type="datetime-local"> theo giờ Việt Nam. */
export function toLocalInput(iso: string | null | undefined): string {
  if (!iso) return '';
  const vn = new Date(new Date(iso).getTime() + 7 * 3600_000);
  return vn.toISOString().slice(0, 16);
}

/** Giá trị <input type="datetime-local"> (hiểu là giờ Việt Nam) → Instant ISO. */
export function fromLocalInput(v: string): string | null {
  if (!v) return null;
  return new Date(v + ':00+07:00').toISOString();
}

export function hm(minutes: number | null | undefined): string {
  if (minutes == null) return '—';
  return `${Math.floor(minutes / 60)}g${String(minutes % 60).padStart(2, '0')}`;
}

export const docStatusLabel: Record<string, string> = {
  DRAFT: 'Nháp',
  CONFIRMED: 'Đã xác nhận',
  CANCELLED: 'Đã hủy',
  ISSUED: 'Đã xuất, chờ bếp nhận',
  RECEIVED: 'Bếp đã nhận',
};

export const expenseCategoryLabel: Record<string, string> = {
  INGREDIENT: 'Nguyên liệu',
  SUPPLIES: 'Vật dụng',
  UTILITY: 'Điện nước',
  RENT: 'Thuê mặt bằng',
  MAINTENANCE: 'Sửa chữa',
  OTHER: 'Khác',
};

export const adjustmentTypeLabel: Record<string, string> = {
  DAMAGE: 'Hư hỏng',
  EXPIRED: 'Hết hạn',
  QUALITY: 'Kém chất lượng',
  OTHER: 'Khác',
};

export const movementTypeLabel: Record<string, string> = {
  RECEIPT: 'Nhập kho',
  ISSUE_OUT: 'Xuất cho bếp',
  ISSUE_REVERSAL: 'Hủy phiếu xuất',
  ISSUE_RECEIVED: 'Bếp nhận (chờ sơ chế)',
  DIRECT_IN: 'Bếp nhận (dùng thẳng)',
  PREP_INPUT: 'Sơ chế: đầu vào',
  PREP_OUTPUT: 'Sơ chế: thành phẩm',
  COOK: 'Nấu món',
  RETURN: 'Hoàn trả',
  WASTE: 'Hủy hàng',
  COUNT_ADJUST: 'Chênh lệch kiểm kê',
};
