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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Bảng kitchen_preparations: phiếu sơ chế (lấy một phần WIP của một dòng xuất, ra một hoặc nhiều lô bếp PREPARED). */
@Entity
@Table(name = "kitchen_preparations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KitchenPreparation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_issue_item_id", nullable = false)
    private StockIssueItem stockIssueItem;

    @Column(name = "input_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal inputQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status;

    @Column(name = "prepared_by_id", nullable = false)
    private Long preparedById;

    @Column(name = "prepared_at")
    private Instant preparedAt;

    @Column(name = "loss_reason", length = 255)
    private String lossReason;

    @Column(name = "request_key", nullable = false, length = 64, updatable = false)
    private String requestKey;

    /** SHA-256 nội dung yêu cầu tạo (RequestKeys): gửi lặp cùng khóa khác nội dung → 409. NULL ở dòng tạo trước migration V5. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_hash", length = 64, updatable = false, columnDefinition = "char(64)")
    private String requestHash;

    /** Phiếu nháp: chưa ảnh hưởng tồn kho tới khi xác nhận. */
    public KitchenPreparation(StockIssueItem stockIssueItem, BigDecimal inputQuantity, String lossReason, long preparedById, String requestKey, String requestHash) {
        this.stockIssueItem = stockIssueItem;
        this.inputQuantity = inputQuantity;
        this.lossReason = lossReason;
        this.preparedById = preparedById;
        this.requestKey = requestKey;
        this.requestHash = requestHash;
        this.status = DocumentStatus.DRAFT;
    }

    public void confirm(long actorId, Instant now) {
        status = DocumentStatus.CONFIRMED;
        preparedById = actorId;
        preparedAt = now;
    }

    public Long getStockIssueItemId() {
        return stockIssueItem.getId();
    }
}
