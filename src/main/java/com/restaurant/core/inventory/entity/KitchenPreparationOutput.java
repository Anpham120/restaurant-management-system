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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Bảng kitchen_preparation_outputs: một đầu ra của phiếu sơ chế (loại PREPARED, số lượng, hạn dùng). */
@Entity
@Table(name = "kitchen_preparation_outputs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KitchenPreparationOutput {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kitchen_preparation_id", nullable = false)
    private KitchenPreparation kitchenPreparation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kitchen_ingredient_type_id", nullable = false)
    private KitchenIngredientType kitchenIngredientType;

    @Column(name = "output_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal outputQuantity;

    @Column(name = "usable_until")
    private Instant usableUntil;

    public KitchenPreparationOutput(KitchenPreparation kitchenPreparation, KitchenIngredientType kitchenIngredientType, BigDecimal outputQuantity, Instant usableUntil) {
        this.kitchenPreparation = kitchenPreparation;
        this.kitchenIngredientType = kitchenIngredientType;
        this.outputQuantity = outputQuantity;
        this.usableUntil = usableUntil;
    }

    public void setUsableUntil(Instant usableUntil) {
        this.usableUntil = usableUntil;
    }

    public Long getKitchenPreparationId() {
        return kitchenPreparation.getId();
    }

    public Long getKitchenIngredientTypeId() {
        return kitchenIngredientType.getId();
    }
}
