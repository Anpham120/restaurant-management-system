package com.restaurant.core.finance.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.restaurant.core.finance.dto.response.InvoiceResponse;

/** Truy vấn CHỈ ĐỌC chi tiết hóa đơn (dòng, yêu cầu thanh toán, giao dịch) trả thẳng DTO. Owner: Phạm Duy An. */
@Repository
public class InvoiceQueryRepository {

    private final JdbcClient jdbc;

    public InvoiceQueryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
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
