package com.restaurant.core.inventory.entity;

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
 * Bảng stock_movements: sổ kho chỉ-thêm. Mỗi thay đổi tồn của lô kho chính (raw), phần nhận vào bếp (wip) hoặc lô bếp
 * (kitchen) có đúng một dòng. eventKey duy nhất chống ghi trùng khi một thao tác bị gửi lặp.
 * Tạo bằng các hàm {@code of…} để chỉ ra loại lô và chứng từ gốc.
 */
@Entity
@Table(name = "stock_movements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 30)
    private MovementType movementType;

    @Column(name = "raw_batch_id")
    private Long rawBatchId;

    @Column(name = "wip_issue_item_id")
    private Long wipIssueItemId;

    @Column(name = "kitchen_batch_id")
    private Long kitchenBatchId;

    @Column(name = "quantity_delta", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantityDelta;

    @Column(name = "ref_issue_item_id")
    private Long refIssueItemId;

    @Column(name = "preparation_id")
    private Long preparationId;

    @Column(name = "order_item_id")
    private Long orderItemId;

    @Column(name = "stock_count_item_id")
    private Long stockCountItemId;

    @Column(name = "stock_return_item_id")
    private Long stockReturnItemId;

    @Column(name = "stock_adjustment_item_id")
    private Long stockAdjustmentItemId;

    @Column(length = 255)
    private String reason;

    @Column(name = "event_key", nullable = false, length = 64)
    private String eventKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    private static StockMovement of(MovementType type, BigDecimal delta, String eventKey, Instant now) {
        StockMovement m = new StockMovement();
        m.movementType = type;
        m.quantityDelta = delta;
        m.eventKey = eventKey;
        m.createdAt = now;
        return m;
    }

    /** Lô kho chính (inventory_batches). */
    public static StockMovement ofRawBatch(MovementType type, long rawBatchId, BigDecimal delta, String eventKey, Instant now) {
        StockMovement m = of(type, delta, eventKey, now);
        m.rawBatchId = rawBatchId;
        return m;
    }

    /** Phần nguyên liệu bếp đã nhận nhưng chưa sơ chế (stock_issue_items.wip_remaining_quantity). */
    public static StockMovement ofWipIssueItem(MovementType type, long issueItemId, BigDecimal delta, String eventKey, Instant now) {
        StockMovement m = of(type, delta, eventKey, now);
        m.wipIssueItemId = issueItemId;
        return m;
    }

    /** Lô bếp (kitchen_batches). */
    public static StockMovement ofKitchenBatch(MovementType type, long kitchenBatchId, BigDecimal delta, String eventKey, Instant now) {
        StockMovement m = of(type, delta, eventKey, now);
        m.kitchenBatchId = kitchenBatchId;
        return m;
    }

    public StockMovement refIssueItem(Long id) {
        this.refIssueItemId = id;
        return this;
    }

    public StockMovement preparation(Long id) {
        this.preparationId = id;
        return this;
    }

    public StockMovement orderItem(Long id) {
        this.orderItemId = id;
        return this;
    }

    public StockMovement countItem(Long id) {
        this.stockCountItemId = id;
        return this;
    }

    public StockMovement returnItem(Long id) {
        this.stockReturnItemId = id;
        return this;
    }

    public StockMovement adjustmentItem(Long id) {
        this.stockAdjustmentItemId = id;
        return this;
    }

    public StockMovement reason(String reason) {
        this.reason = reason;
        return this;
    }
}
