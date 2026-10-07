package vn.khoibep.rms.entity;

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
import org.hibernate.annotations.Immutable;


/** How much of one ingredient a portion of a dish takes, in the ingredient's unit (BR-38). Replaced, never edited. */
@Entity
@Table(name = "recipe_line")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecipeLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_item_id")
    private MenuItem menuItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id")
    private InventoryItem item;

    @Column(nullable = false)
    private BigDecimal quantity;

    public RecipeLine(MenuItem menuItem, InventoryItem item, BigDecimal quantity) {
        this.menuItem = menuItem;
        this.item = item;
        this.quantity = quantity;
    }
}
