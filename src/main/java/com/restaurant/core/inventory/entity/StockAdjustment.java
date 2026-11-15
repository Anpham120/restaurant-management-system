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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Bảng stock_adjustments: phiếu hủy hàng (hỏng, hết hạn, kém chất lượng). */
@Entity
@Table(name = "stock_adjustments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status;

    @Column(name = "adjustment_type", nullable = false, length = 20)
    private String adjustmentType;

    @Column(name = "confirmed_by_id")
    private Long confirmedById;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(nullable = false, length = 255)
    private String reason;

    @Column(name = "created_by_id", nullable = false, updatable = false)
    private Long createdById;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "request_key", nullable = false, length = 64, updatable = false)
    private String requestKey;

    /** SHA-256 nội dung yêu cầu tạo (RequestKeys): gửi lặp cùng khóa khác nội dung → 409. NULL ở dòng tạo trước migration V5. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_hash", length = 64, updatable = false, columnDefinition = "char(64)")
    private String requestHash;

    public StockAdjustment(String adjustmentType, String reason, long createdById, String requestKey, String requestHash, Instant now) {
        this.adjustmentType = adjustmentType;
        this.reason = reason;
        this.createdById = createdById;
        this.requestKey = requestKey;
        this.requestHash = requestHash;
        this.createdAt = now;
        this.status = DocumentStatus.DRAFT;
    }

    public void confirm(long actorId, Instant now) {
        status = DocumentStatus.CONFIRMED;
        confirmedById = actorId;
        confirmedAt = now;
    }
}
