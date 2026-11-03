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

/** Bảng operating_expenses: khoản chi hoạt động. Không sửa nội dung; chỉ được hủy (VOIDED) kèm lý do. */
@Entity
@Table(name = "operating_expenses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OperatingExpense {

    public static final String PAID = "PAID";
    public static final String VOIDED = "VOIDED";
    public static final String CASH_DRAWER = "CASH_DRAWER";
    public static final String CASH = "CASH";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_date", nullable = false, updatable = false)
    private LocalDate businessDate;

    @Column(nullable = false, length = 30, updatable = false)
    private String category;

    @Column(nullable = false, length = 255, updatable = false)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "payment_method", nullable = false, length = 20, updatable = false)
    private String paymentMethod;

    @Column(name = "fund_source", nullable = false, length = 20, updatable = false)
    private String fundSource;

    @Column(name = "stock_receipt_id", updatable = false)
    private Long stockReceiptId;

    @Column(name = "recorded_by_id", nullable = false, updatable = false)
    private Long recordedById;

    @Column(name = "paid_at", nullable = false, updatable = false)
    private Instant paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "request_key", nullable = false, length = 64, updatable = false)
    private String requestKey;

    @Column(nullable = false, length = 20)
    private String status = PAID;

    @Column(name = "void_reason", length = 255)
    private String voidReason;

    @Column(name = "voided_by_id")
    private Long voidedById;

    @Column(name = "voided_at")
    private Instant voidedAt;

    public OperatingExpense(String requestKey, String category, String description, BigDecimal amount, String paymentMethod,
                            String fundSource, Long stockReceiptId, long recordedById, Instant paidAt, LocalDate businessDate, Instant now) {
        this.requestKey = requestKey;
        this.category = category;
        this.description = description;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.fundSource = fundSource;
        this.stockReceiptId = stockReceiptId;
        this.recordedById = recordedById;
        this.paidAt = paidAt;
        this.businessDate = businessDate;
        this.createdAt = now;
    }

    public boolean isVoided() {
        return VOIDED.equals(status);
    }

    public void voidExpense(String reason, long actorId, Instant now) {
        status = VOIDED;
        voidReason = reason;
        voidedById = actorId;
        voidedAt = now;
    }
}
