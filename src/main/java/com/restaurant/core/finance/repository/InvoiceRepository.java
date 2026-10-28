package com.restaurant.core.finance.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.restaurant.core.finance.dto.InvoiceResponse;

/** Hóa đơn: chốt, mở lại, miễn thu bằng hàm rc_*; đọc chi tiết. Owner: Phạm Duy An. */
@Repository
public class InvoiceRepository {

    private final JdbcClient jdbc;

    public InvoiceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** rc_checkout_invoice(p_order, p_actor) → id hóa đơn PAYMENT_PENDING (tạo đủ invoice_items, DB tự tính tổng). */
    public long checkout(long orderId, long actorId) {
        return jdbc.sql("SELECT rc_checkout_invoice(:o, :a)").param("o", orderId).param("a", actorId).query(Long.class).single();
    }

    /** rc_reopen_invoice(p_invoice, p_actor, p_reason): PAYMENT_PENDING → DRAFT, hủy yêu cầu thanh toán đang mở. */
    public void reopen(long invoiceId, long actorId, String reason) {
        jdbc.sql("SELECT rc_reopen_invoice(:i, :a, :r)").param("i", invoiceId).param("a", actorId).param("r", reason).query().singleRow();
    }

    /** rc_waive_invoice(p_order, p_actor, p_reason) → id hóa đơn WAIVED (Manager). */
    public long waive(long orderId, long actorId, String reason) {
        return jdbc.sql("SELECT rc_waive_invoice(:o, :a, :r)").param("o", orderId).param("a", actorId).param("r", reason)
                .query(Long.class).single();
    }

    public Optional<Long> findInvoiceIdOfOrder(long orderId) {
        return jdbc.sql("SELECT id FROM invoices WHERE order_id = :o").param("o", orderId).query(Long.class).optional();
    }

    public Optional<InvoiceResponse.Header> findHeader(long invoiceId) {
        return jdbc.sql("""
                        SELECT id AS invoice_id, order_id, status, total, created_at, created_by_id,
                               uncollected_reason, uncollected_at, waived_reason, waived_at
                          FROM invoices WHERE id = :i
                        """)
                .param("i", invoiceId).query(InvoiceResponse.Header.class).optional();
    }

    public List<InvoiceResponse.Line> findLines(long invoiceId) {
        return jdbc.sql("SELECT order_item_id, item_name, quantity, unit_price, line_total FROM invoice_items WHERE invoice_id = :i ORDER BY id")
                .param("i", invoiceId).query(InvoiceResponse.Line.class).list();
    }

    public List<InvoiceResponse.PaymentRequestView> findPaymentRequests(long invoiceId) {
        return jdbc.sql("""
                        SELECT id, method, status, amount_due, cash_received, change_amount, payment_reference, created_at, cancel_reason,
                               CAST(NULL AS text) AS qr_image_url
                          FROM payment_requests WHERE invoice_id = :i ORDER BY id
                        """)
                .param("i", invoiceId).query(InvoiceResponse.PaymentRequestView.class).list();
    }

    public Optional<InvoiceResponse.PaymentView> findPayment(long invoiceId) {
        return jdbc.sql("SELECT id, method, amount, paid_at, business_date FROM payments WHERE invoice_id = :i")
                .param("i", invoiceId).query(InvoiceResponse.PaymentView.class).optional();
    }
}
