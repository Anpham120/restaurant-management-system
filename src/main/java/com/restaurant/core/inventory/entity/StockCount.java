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

/** Bảng stock_counts: phiếu kiểm kê. */
@Entity
@Table(name = "stock_counts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockCount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status = DocumentStatus.DRAFT;

    @Column(length = 255)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by_id", nullable = false, updatable = false)
    private Long createdById;

    @Column(name = "confirmed_by_id")
    private Long confirmedById;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "request_key", nullable = false, length = 64, updatable = false)
    private String requestKey;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_hash", nullable = false, length = 64, updatable = false, columnDefinition = "char(64)")
    private String requestHash;

    public StockCount(String note, long createdById, String requestKey, String requestHash, Instant now) {
        this.note = note;
        this.createdById = createdById;
        this.requestKey = requestKey;
        this.requestHash = requestHash;
        this.createdAt = now;
    }

    public void confirm(long actorId, Instant now) {
        status = DocumentStatus.CONFIRMED;
        confirmedById = actorId;
        confirmedAt = now;
    }
}
