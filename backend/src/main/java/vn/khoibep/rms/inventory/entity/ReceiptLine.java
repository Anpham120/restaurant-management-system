package vn.khoibep.rms.inventory.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;

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
import org.hibernate.annotations.Immutable;

/** One ingredient on a goods receipt: how much came in and the price of one unit (BR-37). */
@Entity
@Table(name = "receipt_line")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReceiptLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_id")
    private GoodsReceipt receipt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id")
    private InventoryItem item;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(nullable = false)
    private long unitPrice;

    ReceiptLine(GoodsReceipt receipt, InventoryItem item, BigDecimal quantity, long unitPrice) {
        this.receipt = receipt;
        this.item = item;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    /** Quantity times price, rounded to the dong. */
    public long lineTotal() {
        return quantity.multiply(BigDecimal.valueOf(unitPrice)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
