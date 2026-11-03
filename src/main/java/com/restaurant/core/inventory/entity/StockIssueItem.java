package com.restaurant.core.inventory.entity;

import java.math.BigDecimal;

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
 * Bảng stock_issue_items: một dòng xuất từ một lô kho chính. Với dòng PREPARE, wipRemainingQuantity là phần bếp đã
 * nhận nhưng chưa sơ chế (WIP).
 */
@Entity
@Table(name = "stock_issue_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockIssueItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_issue_id", nullable = false, updatable = false)
    private Long stockIssueId;

    @Column(name = "inventory_batch_id", nullable = false)
    private Long inventoryBatchId;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_mode", nullable = false, length = 12)
    private FulfillmentMode fulfillmentMode;

    @Column(name = "wip_remaining_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal wipRemainingQuantity = BigDecimal.ZERO;

    public StockIssueItem(long stockIssueId, long inventoryBatchId, BigDecimal quantity, FulfillmentMode fulfillmentMode) {
        this.stockIssueId = stockIssueId;
        this.inventoryBatchId = inventoryBatchId;
        this.quantity = quantity;
        this.fulfillmentMode = fulfillmentMode;
    }

    public void changeWipRemaining(BigDecimal delta) {
        wipRemainingQuantity = wipRemainingQuantity.add(delta);
    }

    public void setWipRemaining(BigDecimal quantity) {
        wipRemainingQuantity = quantity;
    }
}
