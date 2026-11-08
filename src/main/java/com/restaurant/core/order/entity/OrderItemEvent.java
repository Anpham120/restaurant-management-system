package com.restaurant.core.order.entity;

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

/** Bảng order_item_events: nhật ký chỉ-thêm mỗi lần món đổi trạng thái (ai, từ đâu sang đâu, lý do). */
@Entity
@Table(name = "order_item_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItemEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false, updatable = false)
    private OrderItem orderItem;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private Long actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20, updatable = false)
    private OrderItemStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20, updatable = false)
    private OrderItemStatus toStatus;

    @Column(length = 255, updatable = false)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public OrderItemEvent(OrderItem orderItem, long actorId, OrderItemStatus fromStatus, OrderItemStatus toStatus, String reason, Instant now) {
        this.orderItem = orderItem;
        this.actorId = actorId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
        this.createdAt = now;
    }

    public Long getOrderItemId() {
        return orderItem.getId();
    }
}
