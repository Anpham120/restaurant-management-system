import dayjs from 'dayjs'
import type { Order, Payment, PaymentMethod, Settings } from '@/shared/api/types'
import { money, time } from '@/shared/utils/format'
import { billLines, orderTitle } from '../utils/bill'

const METHOD_LABEL: Record<PaymentMethod, string> = { CASH: 'Tiền mặt', BANK_TRANSFER: 'Chuyển khoản' }

interface Props {
  order: Order
  settings: Settings | undefined
  /** Given once the order is paid: the slip becomes a receipt. */
  payment?: Payment
  printedAt?: Date
}

/** FR-08.9, BR-33: a bill or a receipt laid out for an 80 mm receipt printer. */
export default function BillSlip({ order, settings, payment, printedAt = new Date() }: Props) {
  const lines = billLines(order.items)
  return (
    <div className="slip">
      <div className="slip-center slip-strong">{settings?.name}</div>
      {settings?.address && <div className="slip-center">{settings.address}</div>}
      {settings?.phone && <div className="slip-center">ĐT {settings.phone}</div>}
      <div className="slip-center slip-strong slip-title">{payment ? 'PHIẾU THANH TOÁN' : 'PHIẾU TẠM TÍNH'}</div>
      <div>
        {orderTitle(order)} · Đơn #{order.id}
        {order.guestCount ? ` · ${order.guestCount} khách` : ''}
      </div>
      <div>
        Vào {dayjs(order.openedAt).format('HH:mm')} · In {time(printedAt.toISOString())}
      </div>
      <hr />
      {lines.map((line) => (
        <div key={line.id}>
          <div>{line.itemName}</div>
          <div className="slip-row">
            <span>
              {line.quantity} x {money(line.unitPrice)}
            </span>
            <span>{money(line.amount)}</span>
          </div>
        </div>
      ))}
      <hr />
      <div className="slip-row slip-strong">
        <span>TỔNG CỘNG</span>
        <span>{money(order.total)}</span>
      </div>
      {order.pendingCount > 0 && <div>Còn {order.pendingCount} món khách gửi chờ xác nhận, chưa tính.</div>}
      {payment && (
        <>
          <div className="slip-row">
            <span>{METHOD_LABEL[payment.method]}</span>
            <span>{money(payment.amount)}</span>
          </div>
          {payment.receivedAmount !== null && (
            <div className="slip-row">
              <span>Khách đưa</span>
              <span>{money(payment.receivedAmount)}</span>
            </div>
          )}
          {payment.change !== null && (
            <div className="slip-row">
              <span>Tiền thối</span>
              <span>{money(payment.change)}</span>
            </div>
          )}
          {payment.reference && <div>Mã chuyển khoản: {payment.reference}</div>}
          <div>Đã trả lúc {time(payment.paidAt)}</div>
        </>
      )}
      <hr />
      <div className="slip-center">Giá đã gồm VAT.</div>
      <div className="slip-center">Phiếu này không thay hoá đơn GTGT.</div>
      {payment && <div className="slip-center">Cảm ơn quý khách!</div>}
    </div>
  )
}
