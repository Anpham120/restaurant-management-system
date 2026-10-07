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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import vn.khoibep.rms.enums.AdjustmentReason;
import vn.khoibep.rms.enums.AdjustmentStatus;
import vn.khoibep.rms.enums.AdjustmentType;

/** A discount on a bill or a dish given free (FR-08.10, BR-35). Only an APPLIED one takes money off the total. */
@Entity
@Table(name = "adjustment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Adjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private Order order;

    /** The dish given free; null for a discount on the whole bill. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id")
    private OrderItem item;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AdjustmentType type;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AdjustmentReason reason;

    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AdjustmentStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by")
    private Employee createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    /** The manager who approved or rejected it; null when it took effect within the limit. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private Employee decidedBy;

    private Instant decidedAt;

    public Adjustment(Order order, OrderItem item, AdjustmentType type, long amount, AdjustmentReason reason,
                      String note, AdjustmentStatus status, Employee createdBy, Instant createdAt) {
        this.order = order;
        this.item = item;
        this.type = type;
        this.amount = amount;
        this.reason = reason;
        this.note = note;
        this.status = status;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    /** Takes money off the total now. */
    public boolean isInEffect() {
        return status == AdjustmentStatus.APPLIED;
    }

    /** In effect or waiting for a manager: counts toward the limit of the bill. */
    public boolean isOpen() {
        return status == AdjustmentStatus.PENDING || status == AdjustmentStatus.APPLIED;
    }

    public boolean gives(OrderItem dish) {
        return item != null && item.getId().equals(dish.getId());
    }

    public void decide(AdjustmentStatus outcome, Employee manager, Instant at) {
        status = outcome;
        decidedBy = manager;
        decidedAt = at;
    }

    public void cancel() {
        status = AdjustmentStatus.CANCELLED;
    }
}
