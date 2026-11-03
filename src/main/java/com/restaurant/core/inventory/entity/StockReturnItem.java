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

/** Bảng stock_return_items: trả lại một phần của một dòng xuất (WIP, hoặc từ lô bếp DIRECT). */
@Entity
@Table(name = "stock_return_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockReturnItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_return_id", nullable = false)
    private Long stockReturnId;

    @Column(name = "source_issue_item_id", nullable = false)
    private Long sourceIssueItemId;

    @Column(name = "direct_kitchen_batch_id")
    private Long directKitchenBatchId;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    public StockReturnItem(long stockReturnId, long sourceIssueItemId, Long directKitchenBatchId, BigDecimal quantity) {
        this.stockReturnId = stockReturnId;
        this.sourceIssueItemId = sourceIssueItemId;
        this.directKitchenBatchId = directKitchenBatchId;
        this.quantity = quantity;
    }
}
