package com.restaurant.core.inventory.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Bảng inventory_batches: lô nguyên liệu trong kho chính, sinh ra khi xác nhận một dòng phiếu nhập. */
@Entity
@Table(name = "inventory_batches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_receipt_item_id", nullable = false, updatable = false)
    private StockReceiptItem stockReceiptItem;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "quantity_on_hand", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantityOnHand;

    @Column(name = "is_usable", nullable = false)
    private boolean usable = true;

    @Column(name = "unusable_reason", length = 255)
    private String unusableReason;

    @Column(name = "unusable_marked_by_id")
    private Long unusableMarkedById;

    @Column(name = "unusable_marked_at")
    private Instant unusableMarkedAt;

    public InventoryBatch(StockReceiptItem stockReceiptItem, BigDecimal quantity, Instant receivedAt) {
        this.stockReceiptItem = stockReceiptItem;
        this.quantityOnHand = quantity;
        this.receivedAt = receivedAt;
    }

    /** Cộng/trừ tồn. delta âm là trừ. */
    public void changeOnHand(BigDecimal delta) {
        quantityOnHand = quantityOnHand.add(delta);
    }

    public void setOnHand(BigDecimal quantity) {
        quantityOnHand = quantity;
    }

    public void markUnusable(String reason, long actorId, Instant now) {
        usable = false;
        unusableReason = reason;
        unusableMarkedById = actorId;
        unusableMarkedAt = now;
    }

    public Long getStockReceiptItemId() {
        return stockReceiptItem.getId();
    }
}
