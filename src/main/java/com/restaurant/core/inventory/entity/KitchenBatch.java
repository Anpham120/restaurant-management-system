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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Bảng kitchen_batches: lô nguyên liệu đang ở bếp (từ sơ chế hoặc xuất thẳng).
 * Số dư phải luôn khớp tổng sổ kho (stock_movements) — trigger DB kiểm lúc COMMIT. Mọi thay đổi số lượng đi kèm một
 * dòng sổ kho do Service ghi.
 */
@Entity
@Table(name = "kitchen_batches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KitchenBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kitchen_ingredient_type_id", nullable = false)
    private KitchenIngredientType kitchenIngredientType;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preparation_output_id")
    private KitchenPreparationOutput preparationOutput;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "direct_issue_item_id")
    private StockIssueItem directIssueItem;

    @Column(name = "usable_until")
    private Instant usableUntil;

    @Column(name = "quantity_on_hand", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantityOnHand;

    @Column(name = "reserved_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal reservedQuantity = BigDecimal.ZERO;

    @Column(name = "is_usable", nullable = false)
    private boolean usable = true;

    @Column(name = "unusable_reason", length = 255)
    private String unusableReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "unusable_marked_by_id")
    private Long unusableMarkedById;

    @Column(name = "unusable_marked_at")
    private Instant unusableMarkedAt;

    /** Lô sinh ra từ một đầu ra sơ chế. */
    public static KitchenBatch fromPreparationOutput(KitchenIngredientType kitchenIngredientType, KitchenPreparationOutput preparationOutput, Instant usableUntil, BigDecimal quantity,
                                                     Instant now) {
        KitchenBatch b = new KitchenBatch();
        b.kitchenIngredientType = kitchenIngredientType;
        b.preparationOutput = preparationOutput;
        b.usableUntil = usableUntil;
        b.quantityOnHand = quantity;
        b.createdAt = now;
        return b;
    }

    /** Lô sinh ra từ dòng phiếu xuất thẳng cho bếp (không qua sơ chế). */
    public static KitchenBatch fromDirectIssue(KitchenIngredientType kitchenIngredientType, StockIssueItem directIssueItem, Instant usableUntil, BigDecimal quantity, Instant now) {
        KitchenBatch b = new KitchenBatch();
        b.kitchenIngredientType = kitchenIngredientType;
        b.directIssueItem = directIssueItem;
        b.usableUntil = usableUntil;
        b.quantityOnHand = quantity;
        b.createdAt = now;
        return b;
    }

    public BigDecimal getFreeQuantity() {
        return quantityOnHand.subtract(reservedQuantity);
    }

    /** Còn dùng được tại thời điểm {@code now}: chưa bị đánh dấu hỏng và chưa quá hạn dùng. */
    public boolean isUsableAt(Instant now) {
        return usable && (usableUntil == null || usableUntil.isAfter(now));
    }

    public void reserve(BigDecimal quantity) {
        reservedQuantity = reservedQuantity.add(quantity);
    }

    public void cancelReservation(BigDecimal quantity) {
        reservedQuantity = reservedQuantity.subtract(quantity);
    }

    /** Nấu: trừ cả tồn và phần đã giữ. */
    public void consumeReserved(BigDecimal quantity) {
        quantityOnHand = quantityOnHand.subtract(quantity);
        reservedQuantity = reservedQuantity.subtract(quantity);
    }

    /** Cộng/trừ tồn (hoàn trả, hủy, kiểm kê). delta âm là trừ. */
    public void changeOnHand(BigDecimal delta) {
        quantityOnHand = quantityOnHand.add(delta);
    }

    public void markUnusable(String reason, long actorId, Instant now) {
        usable = false;
        unusableReason = reason;
        unusableMarkedById = actorId;
        unusableMarkedAt = now;
    }

    public Long getKitchenIngredientTypeId() {
        return kitchenIngredientType.getId();
    }

    public Long getPreparationOutputId() {
        return preparationOutput == null ? null : preparationOutput.getId();
    }

    public Long getDirectIssueItemId() {
        return directIssueItem == null ? null : directIssueItem.getId();
    }
}
