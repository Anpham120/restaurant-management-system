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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raw_batch_id")
    private InventoryBatch rawBatch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wip_issue_item_id")
    private StockIssueItem wipIssueItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kitchen_batch_id")
    private KitchenBatch kitchenBatch;

    @Column(name = "quantity_delta", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantityDelta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ref_issue_item_id")
    private StockIssueItem refIssueItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preparation_id")
    private KitchenPreparation preparation;

    @Column(name = "order_item_id")
    private Long orderItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_count_item_id")
    private StockCountItem stockCountItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_return_item_id")
    private StockReturnItem stockReturnItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_adjustment_item_id")
    private StockAdjustmentItem stockAdjustmentItem;

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
    public static StockMovement ofRawBatch(MovementType type, InventoryBatch rawBatch, BigDecimal delta, String eventKey, Instant now) {
        StockMovement m = of(type, delta, eventKey, now);
        m.rawBatch = rawBatch;
        return m;
    }

    /** Phần nguyên liệu bếp đã nhận nhưng chưa sơ chế (stock_issue_items.wip_remaining_quantity). */
    public static StockMovement ofWipIssueItem(MovementType type, StockIssueItem wipIssueItem, BigDecimal delta, String eventKey, Instant now) {
        StockMovement m = of(type, delta, eventKey, now);
        m.wipIssueItem = wipIssueItem;
        return m;
    }

    /** Lô bếp (kitchen_batches). */
    public static StockMovement ofKitchenBatch(MovementType type, KitchenBatch kitchenBatch, BigDecimal delta, String eventKey, Instant now) {
        StockMovement m = of(type, delta, eventKey, now);
        m.kitchenBatch = kitchenBatch;
        return m;
    }

    public StockMovement refIssueItem(StockIssueItem item) {
        this.refIssueItem = item;
        return this;
    }

    public StockMovement preparation(KitchenPreparation preparation) {
        this.preparation = preparation;
        return this;
    }

    public StockMovement orderItem(Long id) {
        this.orderItemId = id;
        return this;
    }

    public StockMovement countItem(StockCountItem item) {
        this.stockCountItem = item;
        return this;
    }

    public StockMovement returnItem(StockReturnItem item) {
        this.stockReturnItem = item;
        return this;
    }

    public StockMovement adjustmentItem(StockAdjustmentItem item) {
        this.stockAdjustmentItem = item;
        return this;
    }

    public StockMovement reason(String reason) {
        this.reason = reason;
        return this;
    }

    public Long getRawBatchId() {
        return rawBatch == null ? null : rawBatch.getId();
    }

    public Long getWipIssueItemId() {
        return wipIssueItem == null ? null : wipIssueItem.getId();
    }

    public Long getKitchenBatchId() {
        return kitchenBatch == null ? null : kitchenBatch.getId();
    }

    public Long getRefIssueItemId() {
        return refIssueItem == null ? null : refIssueItem.getId();
    }

    public Long getPreparationId() {
        return preparation == null ? null : preparation.getId();
    }

    public Long getStockCountItemId() {
        return stockCountItem == null ? null : stockCountItem.getId();
    }

    public Long getStockReturnItemId() {
        return stockReturnItem == null ? null : stockReturnItem.getId();
    }

    public Long getStockAdjustmentItemId() {
        return stockAdjustmentItem == null ? null : stockAdjustmentItem.getId();
    }
}
