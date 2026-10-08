package vn.khoibep.rms.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
import org.hibernate.annotations.BatchSize;

import vn.khoibep.rms.enums.AdjustmentStatus;
import vn.khoibep.rms.enums.Channel;
import vn.khoibep.rms.enums.ItemStatus;
import vn.khoibep.rms.enums.OrderStatus;
import vn.khoibep.rms.enums.OrderType;
import vn.khoibep.rms.enums.PaymentStatus;

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

    /** The booking the order was opened from; its deposit comes off the bill (BR-42). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    /** BR-47: the delivery app of an app order, null for any other; the code is the one the app gave it. */
    @Enumerated(EnumType.STRING)
    private Channel channel;

    private String appOrderCode;

    /** The guest, found by phone number (BR-44). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<OrderItem> items = new ArrayList<>();

    /** Every table the order holds or held (BR-36); loaded in batches for the floor plan. */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    @OrderBy("id")
    @BatchSize(size = 50)
    private List<OrderTable> tableLinks = new ArrayList<>();

    /** Every payment asked for or taken, several when the bill is split (BR-43); loaded in batches for lists. */
    @OneToMany(mappedBy = "order")
    @OrderBy("id")
    @BatchSize(size = 50)
    private List<Payment> payments = new ArrayList<>();

    /** Discounts and dishes given free, in every status (BR-35). */
    @OneToMany(mappedBy = "order")
    @OrderBy("id")
    private List<Adjustment> adjustments = new ArrayList<>();

    public boolean isOpen() {
        return status == OrderStatus.OPEN;
    }

    /** BR-12: sum of confirmed, not cancelled dishes, before any discount. */
    public long subtotal() {
        return items.stream().filter(i -> i.getStatus().isBillable()).mapToLong(OrderItem::lineTotal).sum();
    }

    /** BR-35: what the adjustments in effect take off. */
    public long discountTotal() {
        return adjustments.stream().filter(Adjustment::isInEffect).mapToLong(Adjustment::getAmount).sum();
    }

    /** BR-42: the deposit of the booking, as far as it covers the bill after discounts. */
    public long depositCredit() {
        return reservation == null ? 0 : reservation.depositCredit(subtotal() - discountTotal());
    }

    /** BR-12: what the guest pays, never below zero. */
    public long total() {
        return Math.max(0, subtotal() - discountTotal() - depositCredit());
    }

    /** BR-43: what the payments taken so far add up to. */
    public long paidAmount() {
        return payments.stream().filter(p -> p.getStatus() == PaymentStatus.PAID).mapToLong(Payment::getAmount).sum();
    }

    /** BR-13, BR-43: what is left to pay. */
    public long due() {
        return Math.max(0, total() - paidAmount());
    }

    /** BR-42: the bill is paid, so the deposit that came off it is fixed, and counts as revenue. */
    public void applyDeposit() {
        if (reservation != null) {
            reservation.applyDeposit(subtotal() - discountTotal());
        }
    }

    /** BR-13: a bill is not paid while a manager has a discount on it to decide. */
    public int countPendingAdjustments() {
        return (int) adjustments.stream().filter(a -> a.getStatus() == AdjustmentStatus.PENDING).count();
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

    /** BR-36: a closed order gives back every table it held. */
    public void close(OrderStatus finalStatus) {
        status = finalStatus;
        closedAt = Instant.now();
        tableLinks.stream().filter(OrderTable::isActive).forEach(l -> l.release(closedAt));
    }

    /** The main table: the first one the order holds. Null for takeaway. */
    public Long tableId() {
        return table == null ? null : table.getId();
    }

    /** BR-04: the tables the order holds now, the main one first. */
    public List<DiningTable> activeTables() {
        return tableLinks.stream().filter(OrderTable::isActive).map(OrderTable::getTable).toList();
    }

    /** "B05 + B06" for tables put together; the main table once the order has closed. */
    public String tableLabel() {
        List<DiningTable> held = activeTables();
        if (held.isEmpty()) {
            return table == null ? null : table.getName();
        }
        return held.stream().map(DiningTable::getName).collect(Collectors.joining(" + "));
    }

    /** QR tokens of the tables the order holds, so the guest pages at all of them are notified. */
    public List<String> guestTokens() {
        return activeTables().stream().map(DiningTable::getQrToken).toList();
    }

    /** FR-05.1: an order opened at a table holds it. */
    public void hold(DiningTable diningTable) {
        if (table == null) {
            table = diningTable;
        }
        tableLinks.add(new OrderTable(this, diningTable, Instant.now()));
    }

    /**
     * BR-36: the order now holds exactly {@code wanted}, the first being the main table. Tables left out are given
     * back, new ones are taken, the bill does not change.
     */
    public void moveTo(List<DiningTable> wanted) {
        Instant now = Instant.now();
        Set<Long> keep = wanted.stream().map(DiningTable::getId).collect(Collectors.toSet());
        tableLinks.stream().filter(l -> l.isActive() && !keep.contains(l.getTable().getId()))
                .forEach(l -> l.release(now));
        Set<Long> held = activeTables().stream().map(DiningTable::getId).collect(Collectors.toSet());
        wanted.stream().filter(t -> !held.contains(t.getId()))
                .forEach(t -> tableLinks.add(new OrderTable(this, t, now)));
        table = wanted.getFirst();
    }
}
