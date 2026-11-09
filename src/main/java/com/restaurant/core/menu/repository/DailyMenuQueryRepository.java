package com.restaurant.core.menu.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.restaurant.core.menu.dto.response.DailyMenuItemResponse;
import com.restaurant.core.menu.dto.response.DailyMenuResponse;
import com.restaurant.core.menu.dto.response.IngredientUsageRow;

/** Truy vấn CHỈ ĐỌC thực đơn ngày (ghép món, nhóm, công thức; nguyên liệu dự kiến). Ghi: JpaRepository. Owner: Lê Anh. */
@Repository
public class DailyMenuQueryRepository {

    private static final String ITEM_SELECT = """
            SELECT d.id AS daily_menu_item_id, d.daily_menu_id, d.menu_item_id, m.name, c.name AS category_name, m.price,
                   d.recipe_id, r.version AS recipe_version, d.quota_total, 0 AS quota_used, CAST(NULL AS bigint) AS remaining,
                   d.is_paused AS paused, d.pause_reason, m.is_active AS menu_item_active
              FROM daily_menu_items d
              JOIN menu_items m ON m.id = d.menu_item_id
              JOIN menu_categories c ON c.id = m.menu_category_id
              JOIN recipes r ON r.id = d.recipe_id
            """;

    private static final String HEADER_SELECT = "SELECT id, business_date, status, published_at FROM daily_menus";

    private final JdbcClient jdbc;

    public DailyMenuQueryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<DailyMenuResponse.Header> findDailyMenuHeader(long id) {
        return jdbc.sql(HEADER_SELECT + " WHERE id = :id").param("id", id).query(DailyMenuResponse.Header.class).optional();
    }

    public Optional<DailyMenuResponse.Header> findHeaderByDate(LocalDate date) {
        return jdbc.sql(HEADER_SELECT + " WHERE business_date = :d").param("d", date).query(DailyMenuResponse.Header.class).optional();
    }

    public List<DailyMenuItemResponse> findDailyMenuItems(long dailyMenuId) {
        return jdbc.sql(ITEM_SELECT + " WHERE d.daily_menu_id = :m ORDER BY c.sort_order, m.name").param("m", dailyMenuId)
                .query(DailyMenuItemResponse.class).list();
    }

    public Optional<DailyMenuItemResponse> findDailyMenuItem(long dailyMenuItemId) {
        return jdbc.sql(ITEM_SELECT + " WHERE d.id = :id").param("id", dailyMenuItemId).query(DailyMenuItemResponse.class).optional();
    }

    public Optional<DailyMenuResponse.Header> findPreviousHeader(LocalDate before) {
        return jdbc.sql(HEADER_SELECT + " WHERE business_date < :d ORDER BY business_date DESC LIMIT 1").param("d", before)
                .query(DailyMenuResponse.Header.class).optional();
    }






    /**
     * Nguyên liệu bếp của các món đã mở bán (hạn mức khác 0 hoặc đã bán) trong thực đơn: dự kiến theo hạn mức,
     * tiêu hao thực tế (sổ COOK của chính các món này) và tồn bếp tính từ sổ kho tới cuối ngày kinh doanh.
     */
    public List<IngredientUsageRow> findIngredientUsage(long dailyMenuId, LocalDate businessDate, List<Long> soldDailyMenuItemIds) {
        return jdbc.sql("""
                        WITH dmi AS (
                            SELECT d.id, d.recipe_id, d.quota_total, d.id = ANY (:sold) AS sold
                              FROM daily_menu_items d WHERE d.daily_menu_id = :m
                        ), plan AS (
                            SELECT ri.kitchen_ingredient_type_id AS type_id,
                                   CASE WHEN bool_or(x.quota_total IS NULL) THEN NULL
                                        ELSE SUM(x.quota_total * ri.quantity_per_serving) END AS planned,
                                   bool_or(x.quota_total IS NULL) AS has_unlimited
                              FROM dmi x JOIN recipe_ingredients ri ON ri.recipe_id = x.recipe_id
                             WHERE x.quota_total IS DISTINCT FROM 0 OR x.sold      -- bỏ món chưa mở bán; NULL = không giới hạn vẫn tính
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
                .param("m", dailyMenuId).param("d", businessDate)
                .param("sold", soldDailyMenuItemIds.toArray(Long[]::new)).query(IngredientUsageRow.class).list();
    }








}
