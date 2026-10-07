package vn.khoibep.rms.entity;

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
import lombok.Setter;

import vn.khoibep.rms.common.exception.ApiException;
import vn.khoibep.rms.enums.ItemSource;
import vn.khoibep.rms.enums.ItemStatus;

@Entity
@Table(name = "order_item")
@Getter
@Setter
@NoArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_item_id")
    private MenuItem menuItem;

    /** Name and price as ordered (BR-05). */
    @Column(nullable = false)
    private String itemName;

    @Column(nullable = false)
    private long unitPrice;

    @Column(nullable = false)
    private int quantity;

    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemSource source;

    private String cancelReason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** When the dish reached the kitchen; null while it waits for confirmation. */
    private Instant sentAt;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    /**
     * Staff dishes go straight to the kitchen; guest dishes wait for confirmation (BR-10). The price is the menu's, or
     * the app's for an app order (BR-47).
     */
    public static OrderItem create(MenuItem menuItem, long unitPrice, int quantity, String note, ItemSource source) {
        OrderItem item = new OrderItem();
        item.menuItem = menuItem;
        item.itemName = menuItem.getName();
        item.unitPrice = unitPrice;
        item.quantity = quantity;
        item.note = note;
        item.source = source;
        if (source == ItemSource.GUEST) {
            item.status = ItemStatus.PENDING;
        } else {
            item.status = ItemStatus.WAITING;
            item.sentAt = item.createdAt;
        }
        return item;
    }

    public long lineTotal() {
        return unitPrice * quantity;
    }

    public void moveTo(ItemStatus next) {
        if (!status.canMoveTo(next)) {
            throw ApiException.conflict("Không thể chuyển món '%s' từ %s sang %s"
                    .formatted(itemName, status.label(), next.label()));
        }
        status = next;
        updatedAt = Instant.now();
        if (next == ItemStatus.WAITING) {
            sentAt = updatedAt;
        }
    }

    public void cancel(String reason) {
        moveTo(ItemStatus.CANCELLED);
        cancelReason = reason;
    }
}
