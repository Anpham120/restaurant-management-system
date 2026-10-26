package com.restaurant.core.finance.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.restaurant.core.common.exception.ApiException;
import com.restaurant.core.finance.dto.InvoiceResponse;
import com.restaurant.core.finance.repository.InvoiceRepository;

/** Chốt, mở lại, miễn thu và xem hóa đơn. Tổng tiền luôn lấy từ DB, không nhận từ client. Owner: Phạm Duy An. */
@Service
public class InvoiceService {

    private final InvoiceRepository invoices;

    public InvoiceService(InvoiceRepository invoices) {
        this.invoices = invoices;
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
        return InvoiceResponse.of(header, invoices.findLines(invoiceId), invoices.findPaymentRequests(invoiceId),
                invoices.findPayment(invoiceId).orElse(null));
    }
}
