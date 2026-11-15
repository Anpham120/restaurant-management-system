package com.restaurant.core.order.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private CustomerOrder order;

    @Column(name = "waiter_id", nullable = false, updatable = false)
    private Long waiterId;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "request_key", nullable = false, length = 64, updatable = false)
    private String requestKey;

    /** SHA-256 nội dung yêu cầu tạo (RequestKeys): gửi lặp cùng khóa khác nội dung → 409. NULL ở dòng tạo trước migration V5. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_hash", length = 64, updatable = false, columnDefinition = "char(64)")
    private String requestHash;

    public OrderBatch(CustomerOrder order, long waiterId, String requestKey, String requestHash, Instant now) {
        this.order = order;
        this.waiterId = waiterId;
        this.requestKey = requestKey;
        this.requestHash = requestHash;
        this.submittedAt = now;
    }

    public Long getOrderId() {
        return order.getId();
    }
}
