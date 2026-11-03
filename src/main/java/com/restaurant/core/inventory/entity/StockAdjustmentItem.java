package com.restaurant.core.inventory.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

    @Column(name = "stock_adjustment_id", nullable = false)
    private Long stockAdjustmentId;

    @Column(name = "raw_batch_id")
    private Long rawBatchId;

    @Column(name = "wip_issue_item_id")
    private Long wipIssueItemId;

    @Column(name = "kitchen_batch_id")
    private Long kitchenBatchId;

    @Column(name = "quantity_delta", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantityDelta;

    @Column(length = 255)
    private String note;

    /** quantityDelta âm: lượng bị hủy. */
    public StockAdjustmentItem(long stockAdjustmentId, Long rawBatchId, Long wipIssueItemId, Long kitchenBatchId, BigDecimal quantityDelta,
                               String note) {
        this.stockAdjustmentId = stockAdjustmentId;
        this.rawBatchId = rawBatchId;
        this.wipIssueItemId = wipIssueItemId;
        this.kitchenBatchId = kitchenBatchId;
        this.quantityDelta = quantityDelta;
        this.note = note;
    }
}
