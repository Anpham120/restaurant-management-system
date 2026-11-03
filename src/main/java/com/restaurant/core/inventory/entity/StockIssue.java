package com.restaurant.core.inventory.entity;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Bảng stock_issues: phiếu xuất kho chính cho bếp. */
@Entity
@Table(name = "stock_issues")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "issued_by_id", nullable = false)
    private Long issuedById;

    @Column(name = "received_by_id")
    private Long receivedById;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IssueStatus status = IssueStatus.DRAFT;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(length = 255)
    private String note;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "cancelled_by_id")
    private Long cancelledById;

    @Column(name = "request_key", nullable = false, length = 64, updatable = false)
    private String requestKey;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_hash", nullable = false, length = 64, updatable = false, columnDefinition = "char(64)")
    private String requestHash;

    public StockIssue(long issuedById, String note, String requestKey, String requestHash, Instant now) {
        this.issuedById = issuedById;
        this.note = note;
        this.requestKey = requestKey;
        this.requestHash = requestHash;
        this.createdAt = now;
    }

    public void markIssued(long actorId, Instant now) {
        status = IssueStatus.ISSUED;
        issuedById = actorId;
        issuedAt = now;
    }

    public void markReceived(long actorId, Instant now) {
        status = IssueStatus.RECEIVED;
        receivedById = actorId;
        receivedAt = now;
    }

    public void cancel(String reason, long actorId, Instant now) {
        status = IssueStatus.CANCELLED;
        cancelReason = reason;
        cancelledById = actorId;
        cancelledAt = now;
    }
}
