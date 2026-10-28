package com.restaurant.core.finance.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.restaurant.core.common.exception.ApiException;
import com.restaurant.core.finance.dto.InvoiceResponse;
import com.restaurant.core.finance.repository.InvoiceRepository;
import com.restaurant.core.finance.sepay.SepayQr;

/** Chốt, mở lại, miễn thu và xem hóa đơn. Tổng tiền luôn lấy từ DB, không nhận từ client. Owner: Phạm Duy An. */
@Service
public class InvoiceService {

    private final InvoiceRepository invoices;
    private final SepayQr qr;

    public InvoiceService(InvoiceRepository invoices, SepayQr qr) {
        this.invoices = invoices;
        this.qr = qr;
    }

    public InvoiceResponse checkout(long orderId, long actorId) {
        return get(invoices.checkout(orderId, actorId));
    }

    public InvoiceResponse reopen(long invoiceId, long actorId, String reason) {
        invoices.reopen(invoiceId, actorId, reason);
        return get(invoiceId);
    }

    public InvoiceResponse waive(long orderId, long actorId, String reason) {
        return get(invoices.waive(orderId, actorId, reason));
    }

    public InvoiceResponse getByOrder(long orderId) {
        return get(invoices.findInvoiceIdOfOrder(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Đơn " + orderId + " chưa có hóa đơn")));
    }

    public InvoiceResponse get(long invoiceId) {
        InvoiceResponse.Header header = invoices.findHeader(invoiceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy hóa đơn " + invoiceId));
        List<InvoiceResponse.PaymentRequestView> requests = invoices.findPaymentRequests(invoiceId).stream()
                .map(r -> "QR".equals(r.method()) && "WAITING_TRANSFER".equals(r.status())
                        ? r.withQrImageUrl(qr.imageUrl(r.amountDue(), r.paymentReference())) : r)
                .toList();
        return InvoiceResponse.of(header, invoices.findLines(invoiceId), requests, invoices.findPayment(invoiceId).orElse(null));
    }
}
