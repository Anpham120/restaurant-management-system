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

/** Bảng stock_count_items: một dòng kiểm kê cho đúng một nguồn (lô kho chính, WIP, hoặc lô bếp). */
@Entity
@Table(name = "stock_count_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockCountItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_count_id", nullable = false, updatable = false)
    private Long stockCountId;

    @Column(name = "raw_batch_id")
    private Long rawBatchId;

    @Column(name = "wip_issue_item_id")
    private Long wipIssueItemId;

    @Column(name = "kitchen_batch_id")
    private Long kitchenBatchId;

    @Column(name = "book_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal bookQuantity;

    @Column(name = "actual_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal actualQuantity;

    @Column(length = 255)
    private String reason;

    public StockCountItem(long stockCountId, Long rawBatchId, Long wipIssueItemId, Long kitchenBatchId, BigDecimal bookQuantity,
                          BigDecimal actualQuantity, String reason) {
        this.stockCountId = stockCountId;
        this.rawBatchId = rawBatchId;
        this.wipIssueItemId = wipIssueItemId;
        this.kitchenBatchId = kitchenBatchId;
        this.bookQuantity = bookQuantity;
        this.actualQuantity = actualQuantity;
        this.reason = reason;
    }

    /** Chênh lệch thực đếm − sổ sách (âm là hao hụt). */
    public BigDecimal calculateDifference() {
        return actualQuantity.subtract(bookQuantity);
    }
}
