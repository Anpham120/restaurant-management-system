package com.restaurant.core.finance.entity;

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
import org.hibernate.annotations.Generated;

/** Bảng invoice_items: một món đã phục vụ trên hóa đơn. line_total do database tính (cột GENERATED). */
@Entity
@Table(name = "invoice_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private Invoice invoice;

    @Column(name = "order_item_id", nullable = false, updatable = false)
    private Long orderItemId;

    @Column(name = "item_name", nullable = false, length = 100, updatable = false)
    private String itemName;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal unitPrice;

    @Generated
    @Column(name = "line_total", precision = 12, scale = 2, insertable = false, updatable = false)
    private BigDecimal lineTotal;

    public InvoiceItem(Invoice invoice, long orderItemId, String itemName, int quantity, BigDecimal unitPrice) {
        this.invoice = invoice;
        this.orderItemId = orderItemId;
        this.itemName = itemName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    /** Thành tiền tính trong Java (dùng trước khi database trả line_total). */
    public BigDecimal calculateLineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public Long getInvoiceId() {
        return invoice.getId();
    }
}
