package com.restaurant.core.inventory.entity;

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

/** Bảng stock_reservations: phần nguyên liệu của một lô bếp được giữ cho một món đã nhận. */
@Entity
@Table(name = "stock_reservations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_item_id", nullable = false, updatable = false)
    private Long orderItemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kitchen_batch_id", nullable = false, updatable = false)
    private KitchenBatch kitchenBatch;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status = ReservationStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "release_reason", length = 30)
    private ReleaseReason releaseReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    public StockReservation(long orderItemId, KitchenBatch kitchenBatch, BigDecimal quantity, Instant now) {
        this.orderItemId = orderItemId;
        this.kitchenBatch = kitchenBatch;
        this.quantity = quantity;
        this.createdAt = now;
    }

    public boolean isActive() {
        return status == ReservationStatus.ACTIVE;
    }

    public void addQuantity(BigDecimal more) {
        quantity = quantity.add(more);
    }

    public void markConsumed(Instant now) {
        status = ReservationStatus.CONSUMED;
        closedAt = now;
    }

    public void release(ReleaseReason reason, Instant now) {
        status = ReservationStatus.RELEASED;
        releaseReason = reason;
        closedAt = now;
    }
}
