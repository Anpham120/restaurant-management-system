package com.restaurant.core.order.entity;

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
import lombok.Setter;

/** Bảng dining_tables. status chỉ đổi khi mở / đóng phiên. */
@Entity
@Table(name = "dining_tables")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DiningTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(nullable = false, length = 20)
    private String code;

    @Setter
    @Column(nullable = false)
    private int capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TableStatus status = TableStatus.AVAILABLE;

    @Setter
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public DiningTable(String code, int capacity) {
        this.code = code;
        this.capacity = capacity;
    }

    public boolean isAvailable() {
        return status == TableStatus.AVAILABLE;
    }

    public void markOccupied() {
        status = TableStatus.OCCUPIED;
    }

    public void markAvailable() {
        status = TableStatus.AVAILABLE;
    }
}
