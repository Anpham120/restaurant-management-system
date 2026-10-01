package vn.khoibep.rms.inventory.entity;

import java.math.BigDecimal;
import java.time.Instant;

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
import lombok.Getter;
import lombok.NoArgsConstructor;

import vn.khoibep.rms.inventory.enums.MovementType;

/** Append-only stock ledger entry. */
@Entity
@Table(name = "stock_movement")
@Getter
@NoArgsConstructor
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id")
    private InventoryItem item;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MovementType type;

    /** Signed: positive adds stock, negative removes it. */
    @Column(nullable = false)
    private BigDecimal quantityChange;

    @Column(nullable = false)
    private BigDecimal quantityAfter;

    private String note;

    private Long createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public StockMovement(InventoryItem item, MovementType type, BigDecimal quantityChange, String note,
                         Long createdBy) {
        this.item = item;
        this.type = type;
        this.quantityChange = quantityChange;
        this.quantityAfter = item.getQuantity();
        this.note = note;
        this.createdBy = createdBy;
    }
}
