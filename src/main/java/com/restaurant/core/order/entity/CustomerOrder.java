package com.restaurant.core.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Bảng orders: đơn của một phiên bàn (1-1 với table_sessions). Tên lớp tránh từ khóa ORDER của JPQL. */
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "table_session_id", nullable = false, updatable = false)
    private TableSession tableSession;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.OPEN;

    public CustomerOrder(TableSession tableSession) {
        this.tableSession = tableSession;
    }

    public boolean isOpen() {
        return status == OrderStatus.OPEN;
    }

    /** Phiên không phát sinh tiền phải thu. */
    public void cancel() {
        status = OrderStatus.CANCELLED;
    }

    /** Hóa đơn đã kết thúc (đã thu, không thu được hoặc miễn thu). */
    public void close() {
        status = OrderStatus.CLOSED;
    }

    public Long getTableSessionId() {
        return tableSession.getId();
    }
}
