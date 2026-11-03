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

/**
 * Bảng payment_webhook_events: mỗi giao dịch SePay báo về được lưu đúng một lần (mã giao dịch duy nhất).
 * Giao dịch không khớp (MISMATCH, AFTER_CANCEL) mở ở trạng thái xử lý OPEN để Quản lý đối soát.
 */
@Entity
@Table(name = "payment_webhook_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_request_id", updatable = false)
    private Long paymentRequestId;

    @Column(name = "provider_transaction_code", nullable = false, length = 64, updatable = false)
    private String providerTransactionCode;

    @Column(name = "payment_reference", nullable = false, length = 64, updatable = false)
    private String paymentReference;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_result", nullable = false, length = 20, updatable = false)
    private WebhookProcessingResult processingResult;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "raw_payload", nullable = false, updatable = false, columnDefinition = "text")
    private String rawPayload;

    @Column(name = "resolution_status", length = 20)
    private String resolutionStatus;

    @Column(name = "resolved_by_id")
    private Long resolvedById;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_note", length = 255)
    private String resolutionNote;

    public PaymentWebhookEvent(Long paymentRequestId, String transactionCode, String reference, BigDecimal amount,
                               WebhookProcessingResult result, String rawPayload, Instant now) {
        this.paymentRequestId = paymentRequestId;
        this.providerTransactionCode = transactionCode;
        this.paymentReference = reference;
        this.amount = amount;
        this.processingResult = result;
        this.rawPayload = rawPayload;
        this.receivedAt = now;
        this.resolutionStatus = result == WebhookProcessingResult.PROCESSED ? null : "OPEN";
    }
}
