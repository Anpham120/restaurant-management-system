package com.restaurant.core.order.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Bảng order_batches: một lượt gửi món xuống bếp. requestKey duy nhất để gửi lặp không tạo món trùng. */
@Entity
@Table(name = "order_batches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "waiter_id", nullable = false, updatable = false)
    private Long waiterId;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "request_key", nullable = false, length = 64, updatable = false)
    private String requestKey;

    public OrderBatch(long orderId, long waiterId, String requestKey, Instant now) {
        this.orderId = orderId;
        this.waiterId = waiterId;
        this.requestKey = requestKey;
        this.submittedAt = now;
    }
}
