package vn.bnn.rms.order;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import vn.bnn.rms.table.DiningTable;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.OPEN;

    /** Null for takeaway. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id")
    private DiningTable table;

    private Integer guestCount;

    private String note;

    /** Employee who opened the order; null when a guest opened it from the table QR. */
    private Long createdBy;

    @Column(nullable = false, updatable = false)
    private Instant openedAt = Instant.now();

    private Instant closedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<OrderItem> items = new ArrayList<>();

    public boolean isOpen() {
        return status == OrderStatus.OPEN;
    }

    /** BR-12: sum of confirmed, not cancelled dishes. */
    public long total() {
        return items.stream().filter(i -> i.getStatus().isBillable()).mapToLong(OrderItem::lineTotal).sum();
    }

    public boolean hasBillableItems() {
        return items.stream().anyMatch(i -> i.getStatus().isBillable());
    }

    public int countItems(ItemStatus status) {
        return (int) items.stream().filter(i -> i.getStatus() == status).count();
    }

    public List<OrderItem> itemsWith(ItemStatus status) {
        return items.stream().filter(i -> i.getStatus() == status).toList();
    }

    public void addItem(OrderItem item) {
        item.setOrder(this);
        items.add(item);
    }

    public void close(OrderStatus finalStatus) {
        status = finalStatus;
        closedAt = Instant.now();
    }

    public Long tableId() {
        return table == null ? null : table.getId();
    }

    /** QR token of the table, used to notify the guest page. */
    public String guestToken() {
        return table == null ? null : table.getQrToken();
    }
}
