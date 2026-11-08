package com.restaurant.core.finance.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.restaurant.core.finance.dto.response.CashReconciliationResponse;

/** Truy vấn CHỈ ĐỌC biên bản đối soát tiền mặt (các phiên bản đã lập trong ngày). Owner: Bùi Đào Đức Anh. */
@Repository
public class CashReconciliationQueryRepository {

    private final JdbcClient jdbc;

    public CashReconciliationQueryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<CashReconciliationResponse.Version> versions(LocalDate date) {
        return jdbc.sql("""
                        SELECT id, version, status, opening_cash, cash_sales, cash_expenses, expected_cash, actual_cash, difference, reason,
                               revision_reason, reconciled_by_id, reconciled_at
                          FROM daily_cash_reconciliations WHERE business_date = :d ORDER BY version DESC""")
                .param("d", date).query(CashReconciliationResponse.Version.class).list();
    }
}
