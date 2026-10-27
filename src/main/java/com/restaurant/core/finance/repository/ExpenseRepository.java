package com.restaurant.core.finance.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.restaurant.core.finance.dto.ExpenseResponse;

/**
 * Khoản chi hoạt động. rc_app chỉ được INSERT các cột nội dung và UPDATE các cột hủy (docs/09); trigger
 * rc_guard_expense kiểm business_date và chặn sửa nội dung. Owner: Bùi Đào Đức Anh.
 */
@Repository
public class ExpenseRepository {

    private static final String SELECT = """
            SELECT id, business_date, category, description, amount, payment_method, fund_source, stock_receipt_id, recorded_by_id,
                   paid_at, created_at, request_key, status, void_reason, voided_by_id, voided_at
              FROM operating_expenses
            """;

    private final JdbcClient jdbc;

    public ExpenseRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Long> findIdByRequestKey(String requestKey) {
        return jdbc.sql("SELECT id FROM operating_expenses WHERE request_key = :k").param("k", requestKey).query(Long.class).optional();
    }

    /** business_date tính trong SQL bằng rc_business_date(paid_at) để khớp guard. */
    public long insert(String requestKey, String category, String description, BigDecimal amount, String paymentMethod, String fundSource,
                       Long stockReceiptId, long actorId, Instant paidAt) {
        return jdbc.sql("""
                        INSERT INTO operating_expenses (business_date, category, description, amount, payment_method, fund_source,
                                                        stock_receipt_id, recorded_by_id, paid_at, request_key)
                        VALUES (rc_business_date(CAST(:p AS timestamptz)), :c, :d, :a, :m, :f, :r, :u, CAST(:p AS timestamptz), :k)
                        RETURNING id""")
                .param("p", java.sql.Timestamp.from(paidAt)).param("c", category).param("d", description).param("a", amount)
                .param("m", paymentMethod).param("f", fundSource).param("r", stockReceiptId).param("u", actorId).param("k", requestKey)
                .query(Long.class).single();
    }

    public int voidExpense(long id, long actorId, String reason) {
        return jdbc.sql("""
                        UPDATE operating_expenses SET status = 'VOIDED', void_reason = :r, voided_by_id = :a, voided_at = now()
                         WHERE id = :id AND status = 'PAID'""")
                .param("id", id).param("a", actorId).param("r", reason).update();
    }

    public Optional<ExpenseResponse> find(long id) {
        return jdbc.sql(SELECT + "WHERE id = :id").param("id", id).query(ExpenseResponse.class).optional();
    }

    public List<ExpenseResponse> find(LocalDate from, LocalDate to, String category, String status) {
        return jdbc.sql(SELECT + """
                         WHERE business_date BETWEEN :f AND :t
                           AND (CAST(:c AS text) IS NULL OR category = :c) AND (CAST(:s AS text) IS NULL OR status = :s)
                         ORDER BY business_date DESC, id DESC""")
                .param("f", from).param("t", to).param("c", category).param("s", status).query(ExpenseResponse.class).list();
    }
}
