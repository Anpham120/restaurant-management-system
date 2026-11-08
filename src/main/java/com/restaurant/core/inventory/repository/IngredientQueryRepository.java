package com.restaurant.core.inventory.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.restaurant.core.inventory.dto.response.IngredientResponses.IngredientResponse;
import com.restaurant.core.inventory.dto.response.IngredientResponses.KitchenTypeResponse;

/** Danh mục nguyên liệu và loại bếp (rc_app ghi trực tiếp). Owner: Đỗ Tuấn Anh. */
@Repository
public class IngredientQueryRepository {

    private static final String INGREDIENT_SELECT =
            "SELECT id, name, base_unit, min_quantity, is_expiry_tracked AS expiry_tracked, is_active AS active FROM ingredients";
    private static final String TYPE_SELECT = """
            SELECT t.id, t.raw_ingredient_id, g.name AS raw_ingredient_name, g.base_unit, t.name, t.handling_type,
                   t.shelf_life_hours, t.minimum_quantity, t.is_active AS active
              FROM kitchen_ingredient_types t JOIN ingredients g ON g.id = t.raw_ingredient_id
            """;

    private final JdbcClient jdbc;

    public IngredientQueryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<IngredientResponse> findIngredients(boolean activeOnly) {
        return jdbc.sql(INGREDIENT_SELECT + " WHERE is_active OR NOT :a ORDER BY name").param("a", activeOnly)
                .query(IngredientResponse.class).list();
    }

    public Optional<IngredientResponse> findIngredient(long id) {
        return jdbc.sql(INGREDIENT_SELECT + " WHERE id = :id").param("id", id).query(IngredientResponse.class).optional();
    }



    public List<KitchenTypeResponse> findTypes(Long ingredientId, boolean activeOnly) {
        return jdbc.sql(TYPE_SELECT + " WHERE (CAST(:g AS bigint) IS NULL OR t.raw_ingredient_id = :g) AND (t.is_active OR NOT :a) ORDER BY g.name, t.name")
                .param("g", ingredientId).param("a", activeOnly).query(KitchenTypeResponse.class).list();
    }

    public Optional<KitchenTypeResponse> findType(long id) {
        return jdbc.sql(TYPE_SELECT + " WHERE t.id = :id").param("id", id).query(KitchenTypeResponse.class).optional();
    }


}
