import { invoiceStatusLabel, itemStatusLabel } from '../lib/format';

const ITEM_TONE: Record<string, string> = {
  PENDING: 'chip-wait',
  ACCEPTED: 'chip-wait',
  COOKING: 'chip-wait',
  READY: 'chip-go',
  SERVED: '',
  REJECTED: 'chip-stop',
  CANCELLED: 'chip-stop',
};

const INVOICE_TONE: Record<string, string> = {
  DRAFT: '',
  PAYMENT_PENDING: 'chip-wait',
  PAID: 'chip-go',
  UNCOLLECTED: 'chip-stop',
  WAIVED: 'chip-ink',
};

export function ItemStatusChip({ status }: { status: string }) {
  return <span className={`chip ${ITEM_TONE[status] ?? ''}`}>{itemStatusLabel[status] ?? status}</span>;
}

export function InvoiceStatusChip({ status }: { status: string }) {
  return <span className={`chip ${INVOICE_TONE[status] ?? ''}`}>{invoiceStatusLabel[status] ?? status}</span>;
}
