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

/** Bảng stock_count_items: một dòng kiểm kê cho đúng một nguồn (lô kho chính, WIP, hoặc lô bếp). */
@Entity
@Table(name = "stock_count_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockCountItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_count_id", nullable = false, updatable = false)
    private StockCount stockCount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raw_batch_id")
    private InventoryBatch rawBatch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wip_issue_item_id")
    private StockIssueItem wipIssueItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kitchen_batch_id")
    private KitchenBatch kitchenBatch;

    @Column(name = "book_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal bookQuantity;

    @Column(name = "actual_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal actualQuantity;

    @Column(length = 255)
    private String reason;

    public StockCountItem(StockCount stockCount, InventoryBatch rawBatch, StockIssueItem wipIssueItem, KitchenBatch kitchenBatch, BigDecimal bookQuantity,
                          BigDecimal actualQuantity, String reason) {
        this.stockCount = stockCount;
        this.rawBatch = rawBatch;
        this.wipIssueItem = wipIssueItem;
        this.kitchenBatch = kitchenBatch;
        this.bookQuantity = bookQuantity;
        this.actualQuantity = actualQuantity;
        this.reason = reason;
    }

    /** Chênh lệch thực đếm − sổ sách (âm là hao hụt). */
    public BigDecimal calculateDifference() {
        return actualQuantity.subtract(bookQuantity);
    }

    public Long getStockCountId() {
        return stockCount.getId();
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
