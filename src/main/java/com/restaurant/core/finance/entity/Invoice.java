package com.restaurant.core.finance.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Bảng invoices: hóa đơn của một đơn (1-1). Tổng tiền luôn tính từ các dòng hóa đơn, không nhận từ client. */
@Entity
@Table(name = "invoices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by_id", nullable = false, updatable = false)
    private Long createdById;

    @Column(name = "uncollected_reason", length = 255)
    private String uncollectedReason;

    @Column(name = "uncollected_by_id")
    private Long uncollectedById;

    @Column(name = "uncollected_at")
    private Instant uncollectedAt;

    @Column(name = "waived_reason", length = 255)
    private String waivedReason;

    @Column(name = "waived_by_id")
    private Long waivedById;

    @Column(name = "waived_at")
    private Instant waivedAt;

    public Invoice(long orderId, long createdById, Instant now) {
        this.orderId = orderId;
        this.createdById = createdById;
        this.createdAt = now;
    }

    public boolean isDraft() {
        return status == InvoiceStatus.DRAFT;
    }

    public boolean isAwaitingPayment() {
        return status == InvoiceStatus.PAYMENT_PENDING;
    }

    public void updateTotal(BigDecimal newTotal) {
        total = newTotal;
    }

    /** Chốt hóa đơn: khóa tổng tiền, chờ thu. */
    public void lockForPayment(BigDecimal finalTotal) {
        total = finalTotal;
        status = InvoiceStatus.PAYMENT_PENDING;
    }

    /** Mở lại để gọi thêm / hủy món. */
    public void reopen() {
        status = InvoiceStatus.DRAFT;
    }

    public void markPaid() {
        status = InvoiceStatus.PAID;
    }

    public void markUncollected(BigDecimal finalTotal, String reason, long actorId, Instant now) {
        total = finalTotal;
        status = InvoiceStatus.UNCOLLECTED;
        uncollectedReason = reason;
        uncollectedById = actorId;
        uncollectedAt = now;
    }

    public void waive(BigDecimal finalTotal, String reason, long actorId, Instant now) {
        total = finalTotal;
        status = InvoiceStatus.WAIVED;
        waivedReason = reason;
        waivedById = actorId;
        waivedAt = now;
    }
}
