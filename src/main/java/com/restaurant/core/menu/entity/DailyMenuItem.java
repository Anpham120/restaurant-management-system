package com.restaurant.core.menu.entity;

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

/**
 * Bảng daily_menu_items: một món trong thực đơn ngày, kèm công thức áp dụng và hạn mức suất.
 * quotaTotal: null = không giới hạn; 0 = hết suất; > 0 = số suất tối đa trong ngày.
 */
@Entity
@Table(name = "daily_menu_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyMenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "daily_menu_id", nullable = false)
    private DailyMenu dailyMenu;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    @Column(name = "recipe_id", nullable = false)
    private Long recipeId;

    @Column(name = "quota_total")
    private Integer quotaTotal;

    @Column(name = "is_paused", nullable = false)
    private boolean paused;

    @Column(name = "pause_reason", length = 255)
    private String pauseReason;

    @Column(name = "paused_by_id")
    private Long pausedById;

    @Column(name = "paused_at")
    private Instant pausedAt;

    public DailyMenuItem(DailyMenu dailyMenu, MenuItem menuItem, long recipeId, Integer quotaTotal) {
        this.dailyMenu = dailyMenu;
        this.menuItem = menuItem;
        this.recipeId = recipeId;
        this.quotaTotal = quotaTotal;
    }

    public boolean hasQuotaLimit() {
        return quotaTotal != null;
    }

    /** null = không giới hạn, 0 = chưa mở bán / hết suất. */
    public void changeQuota(Integer quotaTotal) {
        this.quotaTotal = quotaTotal;
    }

    public void pause(String reason, long actorId, Instant now) {
        paused = true;
        pauseReason = reason;
        pausedById = actorId;
        pausedAt = now;
    }

    public void resume() {
        paused = false;
        pauseReason = null;
        pausedById = null;
        pausedAt = null;
    }
}
