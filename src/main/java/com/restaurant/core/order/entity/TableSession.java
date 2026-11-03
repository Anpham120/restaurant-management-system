package com.restaurant.core.order.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Bảng table_sessions: một lượt khách ngồi bàn, từ lúc mở đến lúc trả bàn. */
@Entity
@Table(name = "table_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TableSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dining_table_id", nullable = false, updatable = false)
    private Long diningTableId;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status = SessionStatus.OPEN;

    @Column(name = "opened_by_id", nullable = false, updatable = false)
    private Long openedById;

    @Column(name = "closed_by_id")
    private Long closedById;

    public TableSession(long diningTableId, long openedById, Instant now) {
        this.diningTableId = diningTableId;
        this.openedById = openedById;
        this.openedAt = now;
    }

    public boolean isOpen() {
        return status == SessionStatus.OPEN;
    }

    public void close(long actorId, Instant now) {
        status = SessionStatus.CLOSED;
        closedAt = now;
        closedById = actorId;
    }
}
