package com.restaurant.core.menu.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Bảng recipes: một phiên bản công thức của món. Mỗi món có đúng một phiên bản hiện hành; phiên bản cũ giữ nguyên để
 * thực đơn ngày đã dùng nó không đổi định lượng.
 */
@Entity
@Table(name = "recipes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "menu_item_id", nullable = false, updatable = false)
    private Long menuItemId;

    @Column(nullable = false, updatable = false)
    private int version;

    @Column(name = "is_current", nullable = false)
    private boolean current;

    public Recipe(long menuItemId, int version) {
        this.menuItemId = menuItemId;
        this.version = version;
        this.current = true;
    }

    public void retire() {
        current = false;
    }
}
