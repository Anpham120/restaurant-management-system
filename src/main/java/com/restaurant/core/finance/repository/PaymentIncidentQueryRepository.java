package com.restaurant.core.finance.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.restaurant.core.finance.dto.response.PaymentIncidentResponse;

/** Đọc sự cố thanh toán (payment_webhook_events có resolution_status). Ghi: PaymentWebhookEventRepository. */
@Repository
public class PaymentIncidentQueryRepository {

    private static final String SELECT = """
            SELECT w.id, w.provider_transaction_code AS transaction_code, w.payment_reference, w.amount, w.processing_result,
                   w.received_at, w.payment_request_id, r.invoice_id, r.status AS payment_request_status, r.amount_due,
                   w.resolution_status, w.resolved_by_id, e.full_name AS resolved_by_name, w.resolved_at, w.resolution_note,
                   %s AS raw_payload
              FROM payment_webhook_events w
              LEFT JOIN payment_requests r ON r.id = w.payment_request_id
              LEFT JOIN employees e ON e.id = w.resolved_by_id
            """;
    private static final String FILTER = " WHERE w.resolution_status IS NOT NULL AND (CAST(:s AS text) IS NULL OR w.resolution_status = :s)";

    private final JdbcClient jdbc;

    public PaymentIncidentQueryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Mới nhất trước. status null = mọi sự cố (OPEN và RESOLVED). */
    public List<PaymentIncidentResponse> findIncidents(String status, int page, int size) {
        return jdbc.sql(SELECT.formatted("NULL") + FILTER + " ORDER BY w.received_at DESC, w.id DESC LIMIT :size OFFSET :off")
                .param("s", status).param("size", size).param("off", (long) page * size)
                .query(PaymentIncidentResponse.class).list();
    }

    public long countIncidents(String status) {
        return jdbc.sql("SELECT count(*) FROM payment_webhook_events w" + FILTER).param("s", status).query(Long.class).single();
    }

    public Optional<PaymentIncidentResponse> findIncident(long id) {
        return jdbc.sql(SELECT.formatted("w.raw_payload") + " WHERE w.id = :id AND w.resolution_status IS NOT NULL")
                .param("id", id).query(PaymentIncidentResponse.class).optional();
    }
}
