package com.restaurant.core.inventory.repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.restaurant.core.common.time.BusinessDateProvider;

@Repository
@RequiredArgsConstructor
public class KitchenStockAvailabilityRepository {

    public record DishIngredient(long dailyMenuItemId, long ingredientTypeId, BigDecimal quantityPerServing) {
    }

    private record AvailableIngredient(long ingredientTypeId, BigDecimal quantity) {
    }

    private final JdbcClient jdbc;

    public List<DishIngredient> findRequirements(Collection<Long> dishIds) {
        return jdbc.sql("""
                SELECT d.id AS daily_menu_item_id, r.kitchen_ingredient_type_id AS ingredient_type_id, r.quantity_per_serving
                  FROM daily_menu_items d JOIN recipe_ingredients r ON r.recipe_id = d.recipe_id
                 WHERE d.id IN (:dishIds)
                """).param("dishIds", dishIds).query(DishIngredient.class).list();
    }

    /** Khóa các lượt gọi dùng chung nguyên liệu theo thứ tự ID, kể cả khi chưa có lô bếp. */
    public void lockPlacement(Collection<Long> typeIds) {
        // Khóa bigint âm riêng cho lượt gọi món. Các khóa namespace khác dùng cặp int.
        jdbc.sql("""
                SELECT CAST(pg_advisory_xact_lock(-id) AS text)
                  FROM kitchen_ingredient_types
                 WHERE id IN (:typeIds)
                 ORDER BY id
                """).param("typeIds", typeIds).query(String.class).list();
    }

    public Map<Long, BigDecimal> findAvailableIngredients(Collection<Long> typeIds, Instant now) {
        // Đọc tồn và món chờ trong cùng snapshot: khi bếp nhận, nhu cầu chuyển từ PENDING sang phần đã giữ.
        return jdbc.sql("""
                SELECT ingredient_type_id, GREATEST(SUM(quantity), 0) AS quantity
                FROM (
                    SELECT kitchen_ingredient_type_id AS ingredient_type_id, quantity_on_hand - reserved_quantity AS quantity
                      FROM kitchen_batches
                     WHERE kitchen_ingredient_type_id IN (:typeIds) AND is_usable AND quantity_on_hand > 0
                       AND (usable_until IS NULL OR usable_until > :now)
                    UNION ALL
                    SELECT r.kitchen_ingredient_type_id, -(r.quantity_per_serving * i.quantity)
                      FROM order_items i
                      JOIN daily_menu_items d ON d.id = i.daily_menu_item_id
                      JOIN daily_menus m ON m.id = d.daily_menu_id
                      JOIN recipe_ingredients r ON r.recipe_id = d.recipe_id
                     WHERE i.status = 'PENDING' AND m.status = 'PUBLISHED' AND m.business_date = :today
                       AND r.kitchen_ingredient_type_id IN (:typeIds)
                ) quantities
                GROUP BY ingredient_type_id
                """).param("typeIds", typeIds).param("now", Timestamp.from(now)).param("today", BusinessDateProvider.businessDateOf(now))
                .query(AvailableIngredient.class).list().stream()
                .collect(Collectors.toMap(AvailableIngredient::ingredientTypeId, AvailableIngredient::quantity));
    }
}
