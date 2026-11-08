package com.restaurant.core.finance.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;

import com.restaurant.core.finance.strategy.PaymentMethod;

/** Bảng payment_requests: một lần thu tiền (tiền mặt thu ngay, hoặc QR chờ chuyển khoản). */
@Entity
@Table(name = "payment_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private Invoice invoice;

    @Column(name = "cashier_id", nullable = false, updatable = false)
    private Long cashierId;

    @Column(name = "amount_due", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amountDue;

    @Column(name = "cash_received", precision = 12, scale = 2, updatable = false)
    private BigDecimal cashReceived;

    @Generated
    @Column(name = "change_amount", precision = 12, scale = 2, insertable = false, updatable = false)
    private BigDecimal changeAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentRequestStatus status;

    @Column(name = "payment_reference", length = 64, updatable = false)
    private String paymentReference;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by_id")
    private Long cancelledById;

    /** Thu tiền mặt: ghi luôn ở trạng thái PAID. */
    public static PaymentRequest paidInCash(Invoice invoice, long cashierId, BigDecimal amountDue, BigDecimal cashReceived, Instant now) {
        PaymentRequest r = new PaymentRequest();
        r.invoice = invoice;
        r.cashierId = cashierId;
        r.amountDue = amountDue;
        r.cashReceived = cashReceived;
        r.method = PaymentMethod.CASH;
        r.status = PaymentRequestStatus.PAID;
        r.createdAt = now;
        return r;
    }

    /** QR: chờ khách chuyển khoản với nội dung chứa mã tham chiếu. */
    public static PaymentRequest waitingForTransfer(Invoice invoice, long cashierId, BigDecimal amountDue, String reference, Instant now) {
        PaymentRequest r = new PaymentRequest();
        r.invoice = invoice;
        r.cashierId = cashierId;
        r.amountDue = amountDue;
        r.method = PaymentMethod.QR;
        r.status = PaymentRequestStatus.WAITING_TRANSFER;
        r.paymentReference = reference;
        r.createdAt = now;
        return r;
    }

    public boolean isOpen() {
        return status.isOpen();
    }

    public void markPaid() {
        status = PaymentRequestStatus.PAID;
    }

    public void cancel(String reason, long actorId, Instant now) {
        status = PaymentRequestStatus.CANCELLED;
        cancelReason = reason;
        cancelledById = actorId;
        cancelledAt = now;
    }

    public Long getInvoiceId() {
        return invoice.getId();
    }
}
