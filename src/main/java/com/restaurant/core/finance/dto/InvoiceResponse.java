package com.restaurant.core.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Hóa đơn và các bản ghi liên quan. status: DRAFT, PAYMENT_PENDING, PAID (đã thu), UNCOLLECTED (khách bỏ về — chưa thu),
 * WAIVED (Manager miễn thu). total luôn do DB tính từ invoice_items.
 */
public record InvoiceResponse(long invoiceId, long orderId, String status, BigDecimal total, Instant createdAt, long createdById,
                              String uncollectedReason, Instant uncollectedAt, String waivedReason, Instant waivedAt,
                              List<Line> lines, List<PaymentRequestView> paymentRequests, PaymentView payment) {

    public record Header(long invoiceId, long orderId, String status, BigDecimal total, Instant createdAt, long createdById,
                         String uncollectedReason, Instant uncollectedAt, String waivedReason, Instant waivedAt) {
    }

    public record Line(long orderItemId, String itemName, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
    }

    public record PaymentRequestView(long id, String method, String status, BigDecimal amountDue, BigDecimal cashReceived,
                                     BigDecimal changeAmount, String paymentReference, Instant createdAt, String cancelReason) {
    }

    public record PaymentView(long id, String method, BigDecimal amount, Instant paidAt, LocalDate businessDate) {
    }

    public static InvoiceResponse of(Header h, List<Line> lines, List<PaymentRequestView> requests, PaymentView payment) {
        return new InvoiceResponse(h.invoiceId(), h.orderId(), h.status(), h.total(), h.createdAt(), h.createdById(),
                h.uncollectedReason(), h.uncollectedAt(), h.waivedReason(), h.waivedAt(), lines, requests, payment);
    }
}
