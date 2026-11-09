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

/** Bảng dining_tables. status chỉ đổi khi mở / đóng phiên. */
@Entity
@Table(name = "dining_tables")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DiningTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false)
    private int capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TableStatus status = TableStatus.AVAILABLE;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public DiningTable(String code, int capacity) {
        this.code = code;
        this.capacity = capacity;
    }

    public void changeDetails(String code, int capacity) {
        this.code = code;
        this.capacity = capacity;
    }

    /** Ngừng dùng bàn (hỏng, bỏ): giữ dòng cho lịch sử phiên, không mở phiên mới được. */
    public void deactivate() {
        active = false;
    }

    public void activate() {
        active = true;
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
