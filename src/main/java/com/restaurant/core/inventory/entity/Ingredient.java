package com.restaurant.core.inventory.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Bảng ingredients: nguyên liệu gốc trong kho chính. */
@Entity
@Table(name = "ingredients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ingredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "base_unit", nullable = false, length = 20)
    private String baseUnit;

    @Column(name = "min_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal minQuantity;

    @Column(name = "is_expiry_tracked", nullable = false)
    private boolean expiryTracked;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    public Ingredient(String name, String baseUnit, BigDecimal minQuantity, boolean expiryTracked, boolean active) {
        changeDetails(name, baseUnit, minQuantity, expiryTracked, active);
    }

    public void changeDetails(String name, String baseUnit, BigDecimal minQuantity, boolean expiryTracked, boolean active) {
        this.name = name;
        this.baseUnit = baseUnit;
        this.minQuantity = minQuantity;
        this.expiryTracked = expiryTracked;
        this.active = active;
    }

    /** Hàm tiện ích: hạn dùng đã qua so với ngày kinh doanh. */
    public static boolean isExpired(LocalDate expiryDate, LocalDate today) {
        return expiryDate != null && expiryDate.isBefore(today);
    }
}
