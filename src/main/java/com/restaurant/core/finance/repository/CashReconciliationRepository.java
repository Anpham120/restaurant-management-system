package com.restaurant.core.finance.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.restaurant.core.finance.dto.CashReconciliationResponse;

/** Đối soát tiền mặt: ghi bằng rc_reconcile_cash, đọc bằng rpt_cash_reconciliation. Owner: Bùi Đào Đức Anh. */
@Repository
public class CashReconciliationRepository {

    private final JdbcClient jdbc;

    public CashReconciliationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long reconcile(LocalDate date, BigDecimal openingCash, BigDecimal actualCash, long actorId, String reason, String revisionReason) {
        return jdbc.sql("SELECT rc_reconcile_cash(:d, :o, :c, :a, :r, :v)").param("d", date).param("o", openingCash).param("c", actualCash)
                .param("a", actorId).param("r", reason).param("v", revisionReason).query(Long.class).single();
    }

    public CashReconciliationResponse.Summary summary(LocalDate date) {
        return jdbc.sql("SELECT * FROM rpt_cash_reconciliation(:d)").param("d", date).query(CashReconciliationResponse.Summary.class).single();
    }

    public List<CashReconciliationResponse.Version> versions(LocalDate date) {
        return jdbc.sql("""
                        SELECT id, version, status, opening_cash, cash_sales, cash_expenses, expected_cash, actual_cash, difference, reason,
                               revision_reason, reconciled_by_id, reconciled_at
                          FROM daily_cash_reconciliations WHERE business_date = :d ORDER BY version DESC""")
                .param("d", date).query(CashReconciliationResponse.Version.class).list();
    }
}
