package vn.khoibep.rms.inventory.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "inventory_item")
@Getter
@Setter
@NoArgsConstructor
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String unit;

    /** Changed only through stock movements (BR-19). */
    @Column(nullable = false)
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal minQuantity = BigDecimal.ZERO;

    /** BR-37: cost of one unit in VND, the weighted average of what was paid; null before the first receipt. */
    private Long unitCost;

    /** BR-20. */
    public boolean isLowStock() {
        return quantity.compareTo(minQuantity) <= 0;
    }

    /**
     * BR-37: stock bought at {@code unitPrice} comes in, and the unit cost becomes the weighted average of the stock
     * already costed and the new stock, rounded to the dong. Without a cost yet, the price is the cost.
     */
    public void receive(BigDecimal received, long unitPrice) {
        BigDecimal costedQuantity = unitCost == null ? BigDecimal.ZERO : quantity;
        BigDecimal costedValue = unitCost == null ? BigDecimal.ZERO : quantity.multiply(BigDecimal.valueOf(unitCost));
        unitCost = costedValue.add(received.multiply(BigDecimal.valueOf(unitPrice)))
                .divide(costedQuantity.add(received), 0, RoundingMode.HALF_UP).longValueExact();
        quantity = quantity.add(received);
    }
}
