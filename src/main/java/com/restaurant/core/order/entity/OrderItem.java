package com.restaurant.core.order.entity;

import java.math.BigDecimal;

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
 * Bảng order_items: một dòng món. Tên và giá chụp lại lúc gọi (snapshot), không đổi theo thực đơn sau này.
 * Đổi trạng thái chỉ qua {@code OrderItemStatusChanger} để luôn có dòng nhật ký order_item_events đi kèm.
 */
@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_batch_id", nullable = false, updatable = false)
    private OrderBatch batch;

    @Column(name = "daily_menu_item_id", nullable = false, updatable = false)
    private Long dailyMenuItemId;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(name = "item_name_snapshot", nullable = false, length = 100, updatable = false)
    private String itemNameSnapshot;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal unitPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderItemStatus status = OrderItemStatus.PENDING;

    @Column(name = "has_stock_exception", nullable = false)
    private boolean hasStockException;

    @Column(length = 255, updatable = false)
    private String note;

    public OrderItem(OrderBatch batch, long dailyMenuItemId, int quantity, String itemNameSnapshot, BigDecimal unitPrice, String note) {
        this.batch = batch;
        this.dailyMenuItemId = dailyMenuItemId;
        this.quantity = quantity;
        this.itemNameSnapshot = itemNameSnapshot;
        this.unitPrice = unitPrice;
        this.note = note;
    }

    public long getOrderId() {
        return batch.getOrderId();
    }

    /** Chỉ gọi từ OrderItemStatusChanger (nơi kiểm bước chuyển hợp lệ và ghi nhật ký). */
    public void changeStatus(OrderItemStatus next) {
        status = next;
    }

    public void markStockException() {
        hasStockException = true;
    }

    public void clearStockException() {
        hasStockException = false;
    }
}
