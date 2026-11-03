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

/** Bảng kitchen_ingredient_types: loại nguyên liệu ở bếp (dùng thẳng DIRECT hoặc đã sơ chế PREPARED) của một nguyên liệu gốc. */
@Entity
@Table(name = "kitchen_ingredient_types")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KitchenIngredientType {

    public static final String DIRECT = "DIRECT";
    public static final String PREPARED = "PREPARED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "raw_ingredient_id", nullable = false)
    private Long rawIngredientId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "handling_type", nullable = false, length = 12)
    private String handlingType;

    @Column(name = "shelf_life_hours")
    private Integer shelfLifeHours;

    @Column(name = "minimum_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal minimumQuantity;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    public KitchenIngredientType(long rawIngredientId, String name, String handlingType, Integer shelfLifeHours, BigDecimal minimumQuantity,
                                 boolean active) {
        this.rawIngredientId = rawIngredientId;
        this.handlingType = handlingType;
        changeDetails(name, shelfLifeHours, minimumQuantity, active);
    }

    /** Nguyên liệu gốc và cách dùng (DIRECT/PREPARED) không đổi sau khi tạo: lô bếp, công thức đã tham chiếu. */
    public void changeDetails(String name, Integer shelfLifeHours, BigDecimal minimumQuantity, boolean active) {
        this.name = name;
        this.shelfLifeHours = shelfLifeHours;
        this.minimumQuantity = minimumQuantity;
        this.active = active;
    }
}
