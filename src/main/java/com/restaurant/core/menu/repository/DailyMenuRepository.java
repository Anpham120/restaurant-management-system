package com.restaurant.core.menu.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.restaurant.core.menu.dto.DailyMenuItemResponse;
import com.restaurant.core.menu.dto.DailyMenuResponse;
import com.restaurant.core.menu.dto.IngredientUsageRow;

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

    public Optional<DailyMenuResponse.Header> findPreviousHeader(LocalDate before) {
        return jdbc.sql(HEADER_SELECT + " WHERE business_date < :d ORDER BY business_date DESC LIMIT 1").param("d", before)
                .query(DailyMenuResponse.Header.class).optional();
    }

    public long insertMenu(LocalDate businessDate) {
        return jdbc.sql("INSERT INTO daily_menus (business_date) VALUES (:d) RETURNING id").param("d", businessDate)
                .query(Long.class).single();
    }

    /**
     * Thực đơn đã công bố của ngày đã qua: sau 04:00 rc_submit_batch/rc_accept_item đã không bán theo chúng nữa,
     * chỉ còn trạng thái hiển thị "Đang bán" sai. Không đụng thực đơn hôm nay hay tương lai.
     */
    public int closePastPublished() {
        return jdbc.sql("UPDATE daily_menus SET status = 'CLOSED' WHERE status = 'PUBLISHED' AND business_date < rc_business_date(now())")
                .update();
    }

    /** Mọi món đang kinh doanh (thuộc nhóm đang dùng) có công thức hiện hành, hạn mức 0 = chưa mở bán. */
    public int insertCatalogItems(long dailyMenuId) {
        return jdbc.sql("""
                        INSERT INTO daily_menu_items (daily_menu_id, menu_item_id, recipe_id, quota_total)
                        SELECT :dm, m.id, r.id, 0
                          FROM menu_items m
                          JOIN menu_categories c ON c.id = m.menu_category_id
                          JOIN recipes r ON r.menu_item_id = m.id AND r.is_current
                         WHERE m.is_active AND c.is_active
                        """)
                .param("dm", dailyMenuId).update();
    }

    /** Hạn mức = số suất đã bán của cùng món ở thực đơn nguồn. Món không có ở thực đơn nguồn giữ nguyên. */
    public int fillQuotasFromSold(long dailyMenuId, long sourceMenuId) {
        return jdbc.sql("""
                        UPDATE daily_menu_items d SET quota_total = rc_quota_used(s.id)
                          FROM daily_menu_items s
                         WHERE d.daily_menu_id = :dm AND s.daily_menu_id = :src AND s.menu_item_id = d.menu_item_id
                        """)
                .param("dm", dailyMenuId).param("src", sourceMenuId).update();
    }

    /**
     * Nguyên liệu bếp của các món đã mở bán (hạn mức khác 0 hoặc đã bán) trong thực đơn: dự kiến theo hạn mức,
     * tiêu hao thực tế (sổ COOK của chính các món này) và tồn bếp tính từ sổ kho tới cuối ngày kinh doanh.
     */
    public List<IngredientUsageRow> ingredientUsage(long dailyMenuId, LocalDate businessDate) {
        return jdbc.sql("""
                        WITH dmi AS (
                            SELECT d.id, d.recipe_id, d.quota_total, rc_quota_used(d.id) AS sold
                              FROM daily_menu_items d WHERE d.daily_menu_id = :m
                        ), plan AS (
                            SELECT ri.kitchen_ingredient_type_id AS type_id,
                                   CASE WHEN bool_or(x.quota_total IS NULL) THEN NULL
                                        ELSE SUM(x.quota_total * ri.quantity_per_serving) END AS planned,
                                   bool_or(x.quota_total IS NULL) AS has_unlimited
                              FROM dmi x JOIN recipe_ingredients ri ON ri.recipe_id = x.recipe_id
                             WHERE x.quota_total IS DISTINCT FROM 0 OR x.sold > 0      -- bỏ món chưa mở bán; NULL = không giới hạn vẫn tính
                             GROUP BY ri.kitchen_ingredient_type_id
                        ), cooked AS (
                            SELECT kb.kitchen_ingredient_type_id AS type_id, -SUM(sm.quantity_delta) AS used
                              FROM stock_movements sm
                              JOIN kitchen_batches kb ON kb.id = sm.kitchen_batch_id
                              JOIN order_items oi ON oi.id = sm.order_item_id
                              JOIN dmi ON dmi.id = oi.daily_menu_item_id
                             WHERE sm.movement_type = 'COOK'
                             GROUP BY kb.kitchen_ingredient_type_id
                        ), stock AS (
                            SELECT kb.kitchen_ingredient_type_id AS type_id, SUM(sm.quantity_delta) AS on_hand
                              FROM stock_movements sm
                              JOIN kitchen_batches kb ON kb.id = sm.kitchen_batch_id
                             WHERE kb.kitchen_ingredient_type_id IN (SELECT type_id FROM plan)
                               AND sm.created_at <= LEAST(now(), ((CAST(:d AS date) + 1) + TIME '04:00') AT TIME ZONE 'Asia/Ho_Chi_Minh')
                             GROUP BY kb.kitchen_ingredient_type_id
                        )
                        SELECT t.id AS kitchen_ingredient_type_id, t.name AS type_name, i.base_unit, p.planned, p.has_unlimited,
                               COALESCE(c.used, 0) AS used, COALESCE(s.on_hand, 0) AS on_hand_at_end
                          FROM plan p
                          JOIN kitchen_ingredient_types t ON t.id = p.type_id
                          JOIN ingredients i ON i.id = t.raw_ingredient_id
                          LEFT JOIN cooked c ON c.type_id = p.type_id
                          LEFT JOIN stock s ON s.type_id = p.type_id
                         ORDER BY t.name
                        """)
                .param("m", dailyMenuId).param("d", businessDate).query(IngredientUsageRow.class).list();
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

    /** Như updateQuota nhưng chỉ đổi khi món thuộc đúng thực đơn đang sửa. */
    public int updateQuotaInMenu(long dailyMenuId, long dailyMenuItemId, Integer quotaTotal) {
        return jdbc.sql("UPDATE daily_menu_items SET quota_total = :q WHERE id = :id AND daily_menu_id = :m")
                .param("m", dailyMenuId).param("id", dailyMenuItemId).param("q", quotaTotal).update();
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
