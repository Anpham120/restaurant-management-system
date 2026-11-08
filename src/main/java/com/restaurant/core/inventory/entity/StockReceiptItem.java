package com.restaurant.core.inventory.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

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

/** Bảng stock_receipt_items: một dòng phiếu nhập (một lô của một nguyên liệu). */
@Entity
@Table(name = "stock_receipt_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockReceiptItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_receipt_id", nullable = false, updatable = false)
    private StockReceipt stockReceipt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @Column(name = "input_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal inputQuantity;

    @Column(name = "input_unit", nullable = false, length = 20)
    private String inputUnit;

    @Column(name = "conversion_factor", nullable = false, precision = 12, scale = 3)
    private BigDecimal conversionFactor;

    @Column(name = "unit_price_per_input_unit", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPricePerInputUnit;

    @Column(name = "lot_code", length = 50)
    private String lotCode;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    public StockReceiptItem(StockReceipt stockReceipt, Ingredient ingredient, BigDecimal inputQuantity, String inputUnit, BigDecimal conversionFactor,
                            BigDecimal unitPricePerInputUnit, String lotCode, LocalDate expiryDate) {
        this.stockReceipt = stockReceipt;
        this.ingredient = ingredient;
        this.inputQuantity = inputQuantity;
        this.inputUnit = inputUnit;
        this.conversionFactor = conversionFactor;
        this.unitPricePerInputUnit = unitPricePerInputUnit;
        this.lotCode = lotCode;
        this.expiryDate = expiryDate;
    }

    /** Số lượng theo đơn vị gốc của nguyên liệu = số nhập × hệ số quy đổi. */
    public BigDecimal calculateBaseQuantity() {
        return inputQuantity.multiply(conversionFactor);
    }

    public Long getStockReceiptId() {
        return stockReceipt.getId();
    }

    public Long getIngredientId() {
        return ingredient.getId();
    }
}
