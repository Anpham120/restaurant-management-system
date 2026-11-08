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

/** Bảng stock_return_items: trả lại một phần của một dòng xuất (WIP, hoặc từ lô bếp DIRECT). */
@Entity
@Table(name = "stock_return_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockReturnItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_return_id", nullable = false)
    private StockReturn stockReturn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_issue_item_id", nullable = false)
    private StockIssueItem sourceIssueItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "direct_kitchen_batch_id")
    private KitchenBatch directKitchenBatch;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    public StockReturnItem(StockReturn stockReturn, StockIssueItem sourceIssueItem, KitchenBatch directKitchenBatch, BigDecimal quantity) {
        this.stockReturn = stockReturn;
        this.sourceIssueItem = sourceIssueItem;
        this.directKitchenBatch = directKitchenBatch;
        this.quantity = quantity;
    }

    public Long getStockReturnId() {
        return stockReturn.getId();
    }

    public Long getSourceIssueItemId() {
        return sourceIssueItem.getId();
    }

    public Long getDirectKitchenBatchId() {
        return directKitchenBatch == null ? null : directKitchenBatch.getId();
    }
}
