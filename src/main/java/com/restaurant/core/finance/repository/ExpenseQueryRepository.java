package com.restaurant.core.finance.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.restaurant.core.finance.dto.response.ExpenseResponse;

/** Truy vấn CHỈ ĐỌC khoản chi (trả thẳng DTO). Ghi: OperatingExpenseRepository. Owner: Bùi Đào Đức Anh. */
@Repository
public class ExpenseQueryRepository {

    private static final String SELECT = """
            SELECT id, business_date, category, description, amount, payment_method, fund_source, stock_receipt_id, recorded_by_id,
                   paid_at, created_at, request_key, status, void_reason, voided_by_id, voided_at
              FROM operating_expenses
            """;

    private final JdbcClient jdbc;

    public ExpenseQueryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
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
