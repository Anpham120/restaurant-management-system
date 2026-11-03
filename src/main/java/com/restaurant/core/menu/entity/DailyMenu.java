package com.restaurant.core.menu.entity;

import java.time.Instant;
import java.time.LocalDate;

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

/** Bảng daily_menus: thực đơn của một ngày kinh doanh. */
@Entity
@Table(name = "daily_menus")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyMenu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DailyMenuStatus status;

    @Column(name = "published_at")
    private Instant publishedAt;

    public DailyMenu(LocalDate businessDate) {
        this.businessDate = businessDate;
        this.status = DailyMenuStatus.DRAFT;
    }

    public boolean isClosed() {
        return status == DailyMenuStatus.CLOSED;
    }

    public void publish(Instant now) {
        status = DailyMenuStatus.PUBLISHED;
        publishedAt = now;
    }

    public void close() {
        status = DailyMenuStatus.CLOSED;
    }

    /** Đang bán cho đúng ngày kinh doanh hôm nay. */
    public boolean isOnSaleOn(LocalDate today) {
        return status == DailyMenuStatus.PUBLISHED && businessDate.equals(today);
    }
}
