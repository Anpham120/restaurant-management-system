package com.restaurant.core.finance.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

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

import com.restaurant.core.finance.strategy.PaymentMethod;

/** Bảng payments: giao dịch đã thu (chỉ-thêm, mỗi hóa đơn tối đa một). */
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_id", nullable = false, updatable = false)
    private Long invoiceId;

    @Column(name = "payment_request_id", nullable = false, updatable = false)
    private Long paymentRequestId;

    @Column(name = "payment_webhook_event_id", updatable = false)
    private Long paymentWebhookEventId;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private PaymentMethod method;

    @Column(name = "paid_at", nullable = false, updatable = false)
    private Instant paidAt;

    @Column(name = "business_date", nullable = false, updatable = false)
    private LocalDate businessDate;

    public Payment(long invoiceId, long paymentRequestId, Long paymentWebhookEventId, BigDecimal amount, PaymentMethod method,
                   Instant paidAt, LocalDate businessDate) {
        this.invoiceId = invoiceId;
        this.paymentRequestId = paymentRequestId;
        this.paymentWebhookEventId = paymentWebhookEventId;
        this.amount = amount;
        this.method = method;
        this.paidAt = paidAt;
        this.businessDate = businessDate;
    }
}
