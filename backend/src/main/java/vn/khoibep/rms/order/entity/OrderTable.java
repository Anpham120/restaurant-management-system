package vn.khoibep.rms.order.entity;

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

import vn.khoibep.rms.table.entity.DiningTable;

/**
 * A table an order holds (BR-04, BR-36). While {@code releasedAt} is null the table is the order's; released rows are
 * the history of the order's tables.
 */
@Entity
@Table(name = "order_table")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "table_id")
    private DiningTable table;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant releasedAt;

    OrderTable(Order order, DiningTable table, Instant createdAt) {
        this.order = order;
        this.table = table;
        this.createdAt = createdAt;
    }

    public boolean isActive() {
        return releasedAt == null;
    }

    void release(Instant at) {
        releasedAt = at;
    }
}
