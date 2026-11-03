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

/** Bảng stock_returns: phiếu bếp trả hàng về kho chính. */
@Entity
@Table(name = "stock_returns")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status;

    @Column(name = "confirmed_by_id")
    private Long confirmedById;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "requested_by_id", nullable = false, updatable = false)
    private Long requestedById;

    @Column(nullable = false, length = 255)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "request_key", nullable = false, length = 64, updatable = false)
    private String requestKey;

    public StockReturn(long requestedById, String reason, String requestKey, Instant now) {
        this.requestedById = requestedById;
        this.reason = reason;
        this.requestKey = requestKey;
        this.createdAt = now;
        this.status = DocumentStatus.DRAFT;
    }

    public void confirm(long actorId, Instant now) {
        status = DocumentStatus.CONFIRMED;
        confirmedById = actorId;
        confirmedAt = now;
    }
}
