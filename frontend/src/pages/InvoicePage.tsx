import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router';

import { api } from '../api/client';
import type { InvoiceResponse, OrderResponse, PaymentResponse } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { ErrorNotice } from '../components/ErrorNotice';
import { ReasonDialog } from '../components/ReasonDialog';
import { InvoiceStatusChip } from '../components/StatusChip';
import { useToast } from '../components/Toast';
import { clock, vnd } from '../lib/format';

/** Yêu cầu QR còn mở (chưa thu, chưa hủy). */
const QR_OPEN = ['REQUESTED', 'WAITING_TRANSFER'];

/** Mệnh giá gợi ý: đúng số tiền, rồi làm tròn lên các mốc khách hay đưa. */
function quickAmounts(total: number): number[] {
  const set = new Set<number>([total]);
  for (const step of [10_000, 50_000, 100_000, 200_000, 500_000]) set.add(Math.ceil(total / step) * step);
  return [...set].filter((n) => n >= total).sort((a, b) => a - b).slice(0, 5);
}

export function InvoicePage() {
  const orderId = Number(useParams().orderId);
  const { hasRole } = useAuth();
  const toast = useToast();
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<'CASH' | 'QR'>('CASH');
  const [cash, setCash] = useState('');
  const [qr, setQr] = useState<PaymentResponse | null>(null);
  const [dialog, setDialog] = useState<'reopen' | 'waive' | 'cancel-qr' | null>(null);

  const order = useQuery({
    queryKey: ['order', orderId],
    queryFn: () => api.get<OrderResponse>(`/api/orders/${orderId}`),
  });
  const hasInvoice = order.data?.session.invoiceId != null;
  const invoice = useQuery({
    queryKey: ['invoice', orderId],
    queryFn: () => api.get<InvoiceResponse>(`/api/orders/${orderId}/invoice`),
    enabled: hasInvoice,
    // đang chờ chuyển khoản: hỏi lại thường xuyên để thấy webhook SePay về
    refetchInterval: (q) =>
      q.state.data?.paymentRequests.some((r) => r.method === 'QR' && QR_OPEN.includes(r.status)) ? 3_000 : false,
  });
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['invoice', orderId] });
    queryClient.invalidateQueries({ queryKey: ['order', orderId] });
    queryClient.invalidateQueries({ queryKey: ['tables'] });
  };

  const checkout = useMutation({
    mutationFn: () => api.post<InvoiceResponse>(`/api/orders/${orderId}/invoice`),
    onSuccess: () => {
      toast('Đã chốt hóa đơn');
      refresh();
    },
  });
  const pay = useMutation({
    mutationFn: (body: { method: 'CASH' | 'QR'; cashReceived?: number }) =>
      api.post<PaymentResponse>(`/api/invoices/${invoice.data!.invoiceId}/payments`, body),
    onSuccess: (res) => {
      if (res.status === 'PAID') toast('Đã thu tiền mặt');
      else setQr(res);
      refresh();
    },
  });
  const reasonAction = useMutation({
    mutationFn: ({ kind, reason }: { kind: 'reopen' | 'waive' | 'cancel-qr'; reason: string }) => {
      if (kind === 'reopen') return api.post(`/api/invoices/${invoice.data!.invoiceId}/reopen`, { reason });
      if (kind === 'waive') return api.post(`/api/orders/${orderId}/waive`, { reason });
      return api.post(`/api/payment-requests/${openQr!.id}/cancel`, { reason });
    },
    onSuccess: (_d, v) => {
      toast(v.kind === 'reopen' ? 'Đã mở lại hóa đơn' : v.kind === 'waive' ? 'Đã miễn thu hóa đơn' : 'Đã hủy mã QR');
      if (v.kind === 'cancel-qr') setQr(null);
      setDialog(null);
      refresh();
    },
  });

  const noInvoice = order.isSuccess && !hasInvoice;
  const inv = invoice.data;
  const openQr = inv?.paymentRequests.find((r) => r.method === 'QR' && QR_OPEN.includes(r.status)) ?? null;
  const cashNumber = Number(cash.replace(/\D/g, '')) || 0;
  const tableCode = order.data?.session.tableCode ?? '';

  return (
    <main className="main">
      <div className="page-head">
        <div>
          <h1>Hóa đơn bàn {tableCode}</h1>
          <p className="muted">Phiếu #{orderId}{inv ? ` · hóa đơn #${inv.invoiceId}` : ''}</p>
        </div>
        <div className="row-between">
          <Link to={`/orders/${orderId}`} className="btn">Về phiếu gọi món</Link>
          <Link to="/tables" className="btn">Sơ đồ bàn</Link>
        </div>
      </div>

      {(order.isLoading || invoice.isLoading) && <div className="center-screen">Đang tải hóa đơn…</div>}
      <ErrorNotice error={order.error ?? invoice.error} />

      {noInvoice && (
        <div className="panel stack" style={{ maxWidth: 560 }}>
          <h2>Chưa chốt hóa đơn</h2>
          <p className="muted">
            Chốt khi mọi món đã phục vụ, bị hủy hoặc bị từ chối. Tổng tiền do hệ thống tính từ các món đã phục vụ.
          </p>
          <ErrorNotice error={checkout.error} />
          <button type="button" className="btn btn-primary" disabled={checkout.isPending} onClick={() => checkout.mutate()}>
            Chốt hóa đơn
          </button>
        </div>
      )}

      {inv && (
        <div className="bill-layout">
          <section className="panel">
            <div className="row-between" style={{ marginBottom: '0.75rem' }}>
              <h2>Chi tiết</h2>
              <InvoiceStatusChip status={inv.status} />
            </div>
            <table className="bill-lines">
              <thead>
                <tr>
                  <th>Món</th>
                  <th className="r">SL</th>
                  <th className="r">Đơn giá</th>
                  <th className="r">Thành tiền</th>
                </tr>
              </thead>
              <tbody>
                {inv.lines.map((l) => (
                  <tr key={l.orderItemId}>
                    <td>{l.itemName}</td>
                    <td className="r num">{l.quantity}</td>
                    <td className="r num">{vnd(l.unitPrice)}</td>
                    <td className="r num">{vnd(l.lineTotal)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div className="bill-total">
              <span>Tổng cộng</span>
              <span className="num">{vnd(inv.total)}</span>
            </div>
            {inv.status === 'DRAFT' && (
              <div className="stack" style={{ marginTop: '1rem' }}>
                <p className="muted">Hóa đơn đang mở lại để gọi thêm hoặc hủy món. Chốt lại khi xong.</p>
                <ErrorNotice error={checkout.error} />
                <button type="button" className="btn btn-primary" disabled={checkout.isPending} onClick={() => checkout.mutate()}>
                  Chốt lại hóa đơn
                </button>
              </div>
            )}
          </section>

          <section className="panel stack" aria-label="Thanh toán">
            {inv.status === 'PAID' && inv.payment && (
              <>
                <div className="notice notice-ok">
                  Đã thu {vnd(inv.payment.amount)} bằng {inv.payment.method === 'CASH' ? 'tiền mặt' : 'chuyển khoản'} lúc{' '}
                  {clock(inv.payment.paidAt)}. Bàn đã được trả.
                </div>
                {inv.paymentRequests
                  .filter((r) => r.method === 'CASH' && r.changeAmount != null && r.status !== 'CANCELLED')
                  .slice(-1)
                  .map((r) => (
                    <div className="change" key={r.id}>
                      <span>Tiền thối cho khách</span>
                      <span className="num">{vnd(r.changeAmount)}</span>
                    </div>
                  ))}
                <Link to="/tables" className="btn btn-primary btn-block">Về sơ đồ bàn</Link>
              </>
            )}

            {(inv.status === 'WAIVED' || inv.status === 'UNCOLLECTED') && (
              <div className="notice notice-warn">
                {inv.status === 'WAIVED' ? `Miễn thu: ${inv.waivedReason}` : `Khách bỏ về: ${inv.uncollectedReason}`}
              </div>
            )}

            {inv.status === 'PAYMENT_PENDING' && !openQr && hasRole('CASHIER', 'MANAGER') && (
              <>
                <div className="tabs" role="tablist">
                  <button type="button" role="tab" aria-selected={tab === 'CASH'} onClick={() => setTab('CASH')}>
                    Tiền mặt
                  </button>
                  <button type="button" role="tab" aria-selected={tab === 'QR'} onClick={() => setTab('QR')}>
                    Chuyển khoản QR
                  </button>
                </div>

                {tab === 'CASH' ? (
                  <form
                    className="stack"
                    onSubmit={(e) => {
                      e.preventDefault();
                      pay.mutate({ method: 'CASH', cashReceived: cashNumber });
                    }}
                  >
                    <div className="field">
                      <label htmlFor="cash">Khách đưa</label>
                      <input id="cash" className="input num" inputMode="numeric" autoFocus value={cash}
                             onChange={(e) => {
                               const n = Number(e.target.value.replace(/\D/g, ''));
                               setCash(n ? new Intl.NumberFormat('vi-VN').format(n) : '');
                             }} />
                    </div>
                    <div className="quick">
                      {quickAmounts(inv.total).map((n) => (
                        <button key={n} type="button" className="btn btn-sm num"
                                onClick={() => setCash(new Intl.NumberFormat('vi-VN').format(n))}>
                          {vnd(n)}
                        </button>
                      ))}
                    </div>
                    <div className="change">
                      <span>Tiền thối</span>
                      <span className="num">{cashNumber >= inv.total ? vnd(cashNumber - inv.total) : '—'}</span>
                    </div>
                    {cashNumber > 0 && cashNumber < inv.total && (
                      <div className="notice notice-warn">Khách đưa còn thiếu {vnd(inv.total - cashNumber)}.</div>
                    )}
                    <ErrorNotice error={pay.error} />
                    <button className="btn btn-primary btn-block" disabled={pay.isPending || cashNumber < inv.total}>
                      Thu {vnd(inv.total)}
                    </button>
                  </form>
                ) : (
                  <div className="stack">
                    <ErrorNotice error={pay.error} />
                    <button type="button" className="btn btn-primary btn-block" disabled={pay.isPending}
                            onClick={() => pay.mutate({ method: 'QR' })}>
                      Tạo mã QR {vnd(inv.total)}
                    </button>
                  </div>
                )}
              </>
            )}

            {inv.status === 'PAYMENT_PENDING' && openQr && (
              <div className="qr-box">
                {(qr?.qrImageUrl ?? openQr.qrImageUrl) ? (
                  <img src={(qr?.qrImageUrl ?? openQr.qrImageUrl)!} alt={`Mã QR chuyển khoản ${vnd(openQr.amountDue)}, nội dung ${openQr.paymentReference}`} />
                ) : (
                  <p className="muted">Chưa cấu hình tài khoản nhận tiền nên không tạo được ảnh QR. Khách chuyển khoản với nội dung bên dưới.</p>
                )}
                <div>
                  <div className="num" style={{ fontSize: 'var(--step-2)', fontWeight: 800 }}>{vnd(openQr.amountDue)}</div>
                  <div>
                    Nội dung chuyển khoản: <b>{openQr.paymentReference}</b>
                  </div>
                </div>
                <span className="chip chip-wait">Đang chờ tiền về</span>
                <button type="button" className="btn btn-danger" onClick={() => setDialog('cancel-qr')}>
                  Hủy mã QR
                </button>
              </div>
            )}

            {inv.status === 'PAYMENT_PENDING' && (
              <div className="stack" style={{ borderTop: '1px dashed var(--line)', paddingTop: '0.75rem' }}>
                {hasRole('CASHIER', 'MANAGER') && (
                  <button type="button" className="btn" onClick={() => setDialog('reopen')}>
                    Mở lại hóa đơn để sửa món
                  </button>
                )}
                {hasRole('MANAGER') && (
                  <button type="button" className="btn btn-danger" onClick={() => setDialog('waive')}>
                    Miễn thu
                  </button>
                )}
              </div>
            )}
            <ErrorNotice error={reasonAction.error} />
          </section>
        </div>
      )}

      <ReasonDialog
        open={dialog != null}
        title={
          dialog === 'reopen' ? 'Mở lại hóa đơn?' : dialog === 'waive' ? `Miễn thu ${vnd(inv?.total)}?` : 'Hủy mã QR đang chờ?'
        }
        description={
          dialog === 'reopen'
            ? 'Yêu cầu thanh toán đang mở bị hủy. Sau khi sửa món, chốt lại hóa đơn.'
            : dialog === 'waive'
              ? 'Hóa đơn kết thúc không thu tiền, không tính vào doanh thu, bàn được trả.'
              : 'Nếu khách đã chuyển khoản, đừng hủy: chờ tiền về.'
        }
        confirmLabel={dialog === 'reopen' ? 'Mở lại' : dialog === 'waive' ? 'Miễn thu' : 'Hủy mã QR'}
        danger={dialog !== 'reopen'}
        onConfirm={(reason) => dialog && reasonAction.mutate({ kind: dialog, reason })}
        onClose={() => setDialog(null)}
      />
    </main>
  );
}
