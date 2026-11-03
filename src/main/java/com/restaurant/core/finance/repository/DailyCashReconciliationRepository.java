package com.restaurant.core.finance.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.restaurant.core.finance.entity.DailyCashReconciliation;

public interface DailyCashReconciliationRepository extends JpaRepository<DailyCashReconciliation, Long> {

    @Query("SELECT COALESCE(MAX(r.version), 0) FROM DailyCashReconciliation r WHERE r.businessDate = :date")
    int findLatestVersion(@Param("date") LocalDate businessDate);

    Optional<DailyCashReconciliation> findByBusinessDateAndStatus(LocalDate businessDate, String status);

    /** Khóa theo ngày (pg_advisory_xact_lock): hai Quản lý lập đối soát cùng ngày thì chạy lần lượt, không trùng phiên bản. */
    @Query(value = "SELECT CAST(pg_advisory_xact_lock(hashtext('daily_cash_reconciliations'), CAST(:date - DATE '2000-01-01' AS int)) AS text)",
            nativeQuery = true)
    String lockBusinessDate(@Param("date") LocalDate businessDate);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.businessDate = :date AND p.method = com.restaurant.core.finance.strategy.PaymentMethod.CASH")
    BigDecimal sumCashSales(@Param("date") LocalDate businessDate);

    /** Khoản chi đã trả bằng tiền trong két (không tính chi chuyển khoản hay đã hủy). */
    @Query("""
            SELECT COALESCE(SUM(e.amount), 0) FROM OperatingExpense e
             WHERE e.businessDate = :date AND e.status = 'PAID' AND e.fundSource = 'CASH_DRAWER'""")
    BigDecimal sumCashDrawerExpenses(@Param("date") LocalDate businessDate);
}
