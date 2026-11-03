package com.restaurant.core.finance.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;

/**
 * Bảng daily_cash_reconciliations: biên bản đối soát tiền mặt cuối ngày. Lập lại trong ngày tạo phiên bản mới,
 * bản cũ chuyển SUPERSEDED. expected_cash và difference do database tính (cột GENERATED).
 */
@Entity
@Table(name = "daily_cash_reconciliations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyCashReconciliation {

    public static final String CURRENT = "CURRENT";
    public static final String SUPERSEDED = "SUPERSEDED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_date", nullable = false, updatable = false)
    private LocalDate businessDate;

    @Column(nullable = false, updatable = false)
    private int version;

    @Column(nullable = false, length = 20)
    private String status = CURRENT;

    @Column(name = "opening_cash", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal openingCash;

    @Column(name = "cash_sales", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal cashSales;

    @Column(name = "cash_expenses", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal cashExpenses;

    @Generated
    @Column(name = "expected_cash", precision = 12, scale = 2, insertable = false, updatable = false)
    private BigDecimal expectedCash;

    @Column(name = "actual_cash", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal actualCash;

    @Generated
    @Column(precision = 12, scale = 2, insertable = false, updatable = false)
    private BigDecimal difference;

    @Column(length = 255, updatable = false)
    private String reason;

    @Column(name = "revision_reason", length = 255, updatable = false)
    private String revisionReason;

    @Column(name = "reconciled_by_id", nullable = false, updatable = false)
    private Long reconciledById;

    @Column(name = "reconciled_at", nullable = false, updatable = false)
    private Instant reconciledAt;

    public DailyCashReconciliation(LocalDate businessDate, int version, BigDecimal openingCash, BigDecimal cashSales,
                                   BigDecimal cashExpenses, BigDecimal actualCash, String reason, String revisionReason,
                                   long reconciledById, Instant now) {
        this.businessDate = businessDate;
        this.version = version;
        this.openingCash = openingCash;
        this.cashSales = cashSales;
        this.cashExpenses = cashExpenses;
        this.actualCash = actualCash;
        this.reason = reason;
        this.revisionReason = revisionReason;
        this.reconciledById = reconciledById;
        this.reconciledAt = now;
    }

    /** Tiền mặt lẽ ra phải có = quỹ đầu ngày + thu tiền mặt − chi từ két. */
    public static BigDecimal calculateExpectedCash(BigDecimal openingCash, BigDecimal cashSales, BigDecimal cashExpenses) {
        return openingCash.add(cashSales).subtract(cashExpenses);
    }

    public void supersede() {
        status = SUPERSEDED;
    }
}
