package com.restaurant.core.menu.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.restaurant.core.menu.dto.DailyMenuItemResponse;
import com.restaurant.core.menu.dto.DailyMenuResponse;

/**
 * Thực đơn ngày và hạn mức. Trigger DB giữ các quy tắc: DRAFT → PUBLISHED (≥ 1 món) → CLOSED; không hạ quota dưới số
 * suất đã chiếm; dòng đã có món gọi thì không xóa/đổi món. Số suất đã chiếm luôn tính bằng rc_quota_used. Owner: Lê Anh.
 */
@Repository
public class DailyMenuRepository {

    private static final String ITEM_SELECT = """
            SELECT d.id AS daily_menu_item_id, d.daily_menu_id, d.menu_item_id, m.name, c.name AS category_name, m.price,
                   d.recipe_id, r.version AS recipe_version, d.quota_total, q.used AS quota_used,
                   CASE WHEN d.quota_total IS NULL THEN NULL ELSE GREATEST(d.quota_total - q.used, 0) END AS remaining,
                   d.is_paused AS paused, d.pause_reason, m.is_active AS menu_item_active
              FROM daily_menu_items d
              JOIN menu_items m ON m.id = d.menu_item_id
              JOIN menu_categories c ON c.id = m.menu_category_id
              JOIN recipes r ON r.id = d.recipe_id
              CROSS JOIN LATERAL (SELECT rc_quota_used(d.id) AS used) q
            """;

    private static final String HEADER_SELECT = "SELECT id, business_date, status, published_at FROM daily_menus";

    private final JdbcClient jdbc;

    public DailyMenuRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public LocalDate currentBusinessDate() {
        return jdbc.sql("SELECT rc_business_date(now())").query(LocalDate.class).single();
    }

    public Optional<DailyMenuResponse.Header> findHeader(long id) {
        return jdbc.sql(HEADER_SELECT + " WHERE id = :id").param("id", id).query(DailyMenuResponse.Header.class).optional();
    }

    public Optional<DailyMenuResponse.Header> findHeaderByDate(LocalDate date) {
        return jdbc.sql(HEADER_SELECT + " WHERE business_date = :d").param("d", date).query(DailyMenuResponse.Header.class).optional();
    }

    public List<DailyMenuItemResponse> findItems(long dailyMenuId) {
        return jdbc.sql(ITEM_SELECT + " WHERE d.daily_menu_id = :m ORDER BY c.sort_order, m.name").param("m", dailyMenuId)
                .query(DailyMenuItemResponse.class).list();
    }

    public Optional<DailyMenuItemResponse> findItem(long dailyMenuItemId) {
        return jdbc.sql(ITEM_SELECT + " WHERE d.id = :id").param("id", dailyMenuItemId).query(DailyMenuItemResponse.class).optional();
    }

    public long insertMenu(LocalDate businessDate) {
        return jdbc.sql("INSERT INTO daily_menus (business_date) VALUES (:d) RETURNING id").param("d", businessDate)
                .query(Long.class).single();
    }

    /** Thêm món với công thức HIỆN HÀNH. Trả rỗng khi món ngừng kinh doanh hoặc chưa có công thức hiện hành. */
    public Optional<Long> insertItem(long dailyMenuId, long menuItemId, Integer quotaTotal) {
        return jdbc.sql("""
                        INSERT INTO daily_menu_items (daily_menu_id, menu_item_id, recipe_id, quota_total)
                        SELECT :dm, m.id, r.id, :q
                          FROM menu_items m JOIN recipes r ON r.menu_item_id = m.id AND r.is_current
                         WHERE m.id = :mi AND m.is_active
                        RETURNING id
                        """)
                .param("dm", dailyMenuId).param("mi", menuItemId).param("q", quotaTotal).query(Long.class).optional();
    }

    public int updateQuota(long dailyMenuItemId, Integer quotaTotal) {
        return jdbc.sql("UPDATE daily_menu_items SET quota_total = :q WHERE id = :id").param("id", dailyMenuItemId)
                .param("q", quotaTotal).update();
    }

    public int deleteItem(long dailyMenuItemId) {
        return jdbc.sql("DELETE FROM daily_menu_items WHERE id = :id").param("id", dailyMenuItemId).update();
    }

    /** Trigger rc_guard_daily_menu kiểm chuyển trạng thái hợp lệ và thực đơn có món. */
    public int publish(long dailyMenuId) {
        return jdbc.sql("UPDATE daily_menus SET status = 'PUBLISHED', published_at = now() WHERE id = :id").param("id", dailyMenuId).update();
    }

    public int close(long dailyMenuId) {
        return jdbc.sql("UPDATE daily_menus SET status = 'CLOSED' WHERE id = :id").param("id", dailyMenuId).update();
    }

    public int pause(long dailyMenuItemId, long actorId, String reason) {
        return jdbc.sql("""
                        UPDATE daily_menu_items SET is_paused = true, pause_reason = :r, paused_by_id = :a, paused_at = now()
                         WHERE id = :id AND NOT is_paused
                        """)
                .param("id", dailyMenuItemId).param("a", actorId).param("r", reason).update();
    }

    public int resume(long dailyMenuItemId) {
        return jdbc.sql("""
                        UPDATE daily_menu_items SET is_paused = false, pause_reason = NULL, paused_by_id = NULL, paused_at = NULL
                         WHERE id = :id AND is_paused
                        """)
                .param("id", dailyMenuItemId).update();
    }
}
