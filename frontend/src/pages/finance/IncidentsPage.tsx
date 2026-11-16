import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';

import { api } from '../../api/client';
import type { PageResponse, PaymentIncident } from '../../api/types';
import { ErrorNotice } from '../../components/ErrorNotice';
import { ReasonDialog } from '../../components/ReasonDialog';
import { DataTable, PageHead, StatusTag, Tabs, useAction } from '../../components/ui';
import { dateTime, vnd } from '../../lib/format';

/** Vì sao hệ thống không tự ghi nhận khoản chuyển khoản này. */
function why(i: PaymentIncident): string {
  if (i.processingResult === 'AFTER_CANCEL') return 'Tiền về sau khi mã QR đã hủy hoặc hóa đơn đã xong';
  if (i.paymentRequestId == null) return 'Không khớp mã thanh toán nào';
  if (i.amountDue != null && i.amount !== i.amountDue) {
    return i.amount < i.amountDue ? `Chuyển thiếu ${vnd(i.amountDue - i.amount)}` : `Chuyển thừa ${vnd(i.amount - i.amountDue)}`;
  }
  return 'Tiền vào tài khoản khác tài khoản nhận của nhà hàng';
}

/**
 * Khoản chuyển khoản hệ thống không tự ghi nhận được (lệch tiền, sai mã, tiền về sau khi hủy QR). Hệ thống không tự thu:
 * quản lý xử lý với khách (thu bù, hoàn tiền…) rồi đánh dấu đã xử lý kèm ghi chú.
 */
export function IncidentsPage() {
  const [status, setStatus] = useState<'OPEN' | 'RESOLVED'>('OPEN');
  const [resolving, setResolving] = useState<PaymentIncident | null>(null);
  const list = useQuery({
    queryKey: ['payment-incidents', status],
    queryFn: () => api.get<PageResponse<PaymentIncident>>(`/api/payment-incidents?status=${status}&size=100`),
    refetchInterval: 30_000,
  });
  const resolve = useAction(
    (note: string) => api.post(`/api/payment-incidents/${resolving!.id}/resolve`, { note }),
    { success: 'Đã đánh dấu xử lý xong', invalidate: [['payment-incidents']], onSuccess: () => setResolving(null) },
  );

  return (
    <main className="main">
      <PageHead title="Sự cố chuyển khoản"
                subtitle="Khoản khách chuyển mà hệ thống không tự ghi nhận. Xử lý với khách rồi đánh dấu đã xử lý." />
      <Tabs value={status} onChange={setStatus} options={[['OPEN', 'Cần xử lý'], ['RESOLVED', 'Đã xử lý']]} />
      <ErrorNotice error={list.error ?? resolve.error} />
      <DataTable rows={list.data?.items} rowKey={(i) => i.id}
        empty={status === 'OPEN' ? 'Không có khoản chuyển khoản nào cần xử lý.' : 'Chưa có sự cố nào đã xử lý.'}
        columns={[
          { header: 'Lúc', render: (i) => dateTime(i.receivedAt) },
          { header: 'Số tiền', render: (i) => vnd(i.amount), align: 'right' },
          { header: 'Nội dung', render: (i) => i.paymentReference || '—' },
          { header: 'Vấn đề', render: (i) => <><StatusTag tone="wait">{why(i)}</StatusTag>{i.amountDue != null && <div className="muted">Hóa đơn #{i.invoiceId}: phải thu {vnd(i.amountDue)}</div>}</> },
          status === 'OPEN'
            ? { header: '', render: (i) => <button className="btn btn-sm btn-primary" onClick={() => setResolving(i)}>Đã xử lý</button> }
            : { header: 'Cách xử lý', render: (i) => <>{i.resolutionNote}<div className="muted">{i.resolvedByName} · {i.resolvedAt && dateTime(i.resolvedAt)}</div></> },
        ]} />
      <p className="muted" style={{ marginTop: '1rem' }}>
        Khách đã chuyển đúng tiền nhưng bàn vẫn báo chờ? Mở hóa đơn của bàn ở <Link to="/tables">Sơ đồ bàn</Link> và bấm "Tiền đã về, ghi nhận tay".
      </p>

      <ReasonDialog open={resolving != null} title={`Đã xử lý khoản ${vnd(resolving?.amount)}?`}
                    description="Ghi lại cách đã xử lý để đối chiếu sau này. Hệ thống không tự thu hay hoàn tiền."
                    confirmLabel="Đánh dấu đã xử lý"
                    suggestions={['Đã hoàn tiền cho khách', 'Đã thu bù tiền mặt', 'Khớp sao kê, đã ghi nhận tay', 'Không phải tiền của nhà hàng']}
                    onConfirm={(note) => resolve.mutate(note)} onClose={() => setResolving(null)} />
    </main>
  );
}
