package com.restaurant.core.menu.entity;

import java.math.BigDecimal;

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

/** Bảng menu_items: món trong danh mục (tên, giá hiện hành). */
@Entity
@Table(name = "menu_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_category_id", nullable = false)
    private MenuCategory menuCategory;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "image_url", length = 255)
    private String imageUrl;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    public MenuItem(MenuCategory menuCategory, String name, BigDecimal price, String imageUrl, boolean active) {
        changeDetails(menuCategory, name, price, imageUrl, active);
    }

    public void changeDetails(MenuCategory menuCategory, String name, BigDecimal price, String imageUrl, boolean active) {
        this.menuCategory = menuCategory;
        this.name = name;
        this.price = price;
        this.imageUrl = imageUrl;
        this.active = active;
    }

    public Long getMenuCategoryId() {
        return menuCategory.getId();
    }
}
