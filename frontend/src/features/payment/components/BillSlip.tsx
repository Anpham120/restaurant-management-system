import dayjs from 'dayjs'
import type { Order, Payment, PaymentMethod, Settings } from '@/shared/api/types'
import { money, time } from '@/shared/utils/format'
import { adjustmentLabel } from '../utils/adjustment'
import { billLines, orderTitle } from '../utils/bill'

const METHOD_LABEL: Record<PaymentMethod, string> = { CASH: 'Tiền mặt', BANK_TRANSFER: 'Chuyển khoản' }

interface Props {
  order: Order
  settings: Settings | undefined
  /** Given once the bill is covered: the slip becomes a receipt listing each payment (FR-08.13). */
  payments?: Payment[]
  printedAt?: Date
}

/** FR-08.9, BR-33: a bill or a receipt laid out for an 80 mm receipt printer. */
export default function BillSlip({ order, settings, payments, printedAt = new Date() }: Props) {
  const lines = billLines(order.items)
  const receipt = payments !== undefined
  return (
    <div className="slip">
      <div className="slip-center slip-strong">{settings?.name}</div>
      {settings?.address && <div className="slip-center">{settings.address}</div>}
      {settings?.phone && <div className="slip-center">ĐT {settings.phone}</div>}
      <div className="slip-center slip-strong slip-title">{receipt ? 'PHIẾU THANH TOÁN' : 'PHIẾU TẠM TÍNH'}</div>
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
      {(order.discountTotal > 0 || order.depositCredit > 0) && (
        <>
          <div className="slip-row">
            <span>Cộng tiền món</span>
            <span>{money(order.subtotal)}</span>
          </div>
          {order.adjustments
            .filter((a) => a.status === 'APPLIED')
            .map((a) => (
              <div key={`adjustment-${a.id}`} className="slip-row">
                <span>{adjustmentLabel(a)}</span>
                <span>-{money(a.amount)}</span>
              </div>
            ))}
          {order.depositCredit > 0 && (
            <div className="slip-row">
              <span>Cọc đã trả</span>
              <span>-{money(order.depositCredit)}</span>
            </div>
          )}
        </>
      )}
      <div className="slip-row slip-strong">
        <span>TỔNG CỘNG</span>
        <span>{money(order.total)}</span>
      </div>
      {order.pendingCount > 0 && <div>Còn {order.pendingCount} món khách gửi chờ xác nhận, chưa tính.</div>}
      {/* FR-08.13: a bill being split shows what was taken and what is left. */}
      {!receipt && order.paidAmount > 0 && (
        <>
          <div className="slip-row">
            <span>Đã thu</span>
            <span>-{money(order.paidAmount)}</span>
          </div>
          <div className="slip-row slip-strong">
            <span>CÒN PHẢI THU</span>
            <span>{money(order.due)}</span>
          </div>
        </>
      )}
      {payments?.map((payment) => (
        <div key={payment.id}>
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
        </div>
      ))}
      <hr />
      <div className="slip-center">Giá đã gồm VAT.</div>
      <div className="slip-center">Phiếu này không thay hoá đơn GTGT.</div>
      {receipt && <div className="slip-center">Cảm ơn quý khách!</div>}
    </div>
  )
}
