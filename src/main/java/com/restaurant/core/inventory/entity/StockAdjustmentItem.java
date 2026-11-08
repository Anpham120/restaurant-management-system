package com.restaurant.core.inventory.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** Bảng stock_adjustment_items: lượng hủy (quantityDelta luôn âm) của đúng một nguồn. */
@Entity
@Table(name = "stock_adjustment_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockAdjustmentItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_adjustment_id", nullable = false)
    private StockAdjustment stockAdjustment;

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

    @Column(length = 255)
    private String note;

    /** quantityDelta âm: lượng bị hủy. */
    public StockAdjustmentItem(StockAdjustment stockAdjustment, InventoryBatch rawBatch, StockIssueItem wipIssueItem, KitchenBatch kitchenBatch, BigDecimal quantityDelta,
                               String note) {
        this.stockAdjustment = stockAdjustment;
        this.rawBatch = rawBatch;
        this.wipIssueItem = wipIssueItem;
        this.kitchenBatch = kitchenBatch;
        this.quantityDelta = quantityDelta;
        this.note = note;
    }

    public Long getStockAdjustmentId() {
        return stockAdjustment.getId();
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
}
