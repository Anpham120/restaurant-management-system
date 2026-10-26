package com.restaurant.core.finance.repository;

import java.math.BigDecimal;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Thanh toán tại quầy (role rc_app). Webhook SePay dùng kết nối riêng — xem sepay/SepayWebhookRepository. */
@Repository
public class PaymentRepository {

    private final JdbcClient jdbc;

    public PaymentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** rc_pay_cash(p_invoice, p_actor, p_cash_received) → payments.id */
    public long payCash(long invoiceId, long actorId, BigDecimal cashReceived) {
        return jdbc.sql("SELECT rc_pay_cash(:invoiceId, :actorId, :cash)")
                .param("invoiceId", invoiceId)
                .param("actorId", actorId)
                .param("cash", cashReceived)
                .query(Long.class)
                .single();
    }

    /** rc_start_qr(p_invoice, p_actor) → payment_reference */
    public String startQr(long invoiceId, long actorId) {
        return jdbc.sql("SELECT rc_start_qr(:invoiceId, :actorId)")
                .param("invoiceId", invoiceId)
                .param("actorId", actorId)
                .query(String.class)
                .single();
    }

    /** rc_cancel_payment_request(p_request, p_actor, p_reason) */
    public void cancelRequest(long requestId, long actorId, String reason) {
        jdbc.sql("SELECT rc_cancel_payment_request(:p, :a, :r)").param("p", requestId).param("a", actorId).param("r", reason)
                .query().singleRow();
    }

    public BigDecimal findPaymentAmount(long paymentId) {
        return jdbc.sql("SELECT amount FROM payments WHERE id = :p").param("p", paymentId).query(BigDecimal.class).single();
    }

    public BigDecimal findAmountDueByReference(String reference) {
        return jdbc.sql("SELECT amount_due FROM payment_requests WHERE payment_reference = :r").param("r", reference)
                .query(BigDecimal.class).single();
    }

    public long findInvoiceOfRequest(long requestId) {
        return jdbc.sql("SELECT invoice_id FROM payment_requests WHERE id = :p").param("p", requestId).query(Long.class).single();
    }
}
