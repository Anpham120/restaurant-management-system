package com.restaurant.core.inventory.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.restaurant.core.inventory.dto.IngredientDtos.IngredientResponse;
import com.restaurant.core.inventory.dto.IngredientDtos.KitchenTypeResponse;

/** Danh mục nguyên liệu và loại bếp (rc_app ghi trực tiếp). Owner: Đỗ Tuấn Anh. */
@Repository
public class IngredientRepository {

    private static final String INGREDIENT_SELECT =
            "SELECT id, name, base_unit, min_quantity, is_expiry_tracked AS expiry_tracked, is_active AS active FROM ingredients";
    private static final String TYPE_SELECT = """
            SELECT t.id, t.raw_ingredient_id, g.name AS raw_ingredient_name, g.base_unit, t.name, t.handling_type,
                   t.shelf_life_hours, t.minimum_quantity, t.is_active AS active
              FROM kitchen_ingredient_types t JOIN ingredients g ON g.id = t.raw_ingredient_id
            """;

    private final JdbcClient jdbc;

    public IngredientRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<IngredientResponse> findIngredients(boolean activeOnly) {
        return jdbc.sql(INGREDIENT_SELECT + " WHERE is_active OR NOT :a ORDER BY name").param("a", activeOnly)
                .query(IngredientResponse.class).list();
    }

    public Optional<IngredientResponse> findIngredient(long id) {
        return jdbc.sql(INGREDIENT_SELECT + " WHERE id = :id").param("id", id).query(IngredientResponse.class).optional();
    }

    public long insertIngredient(String name, String baseUnit, BigDecimal minQuantity, boolean expiryTracked, boolean active) {
        return jdbc.sql("""
                        INSERT INTO ingredients (name, base_unit, min_quantity, is_expiry_tracked, is_active)
                        VALUES (:n, :u, :m, :e, :a) RETURNING id""")
                .param("n", name).param("u", baseUnit).param("m", minQuantity).param("e", expiryTracked).param("a", active)
                .query(Long.class).single();
    }

    public int updateIngredient(long id, String name, String baseUnit, BigDecimal minQuantity, boolean expiryTracked, boolean active) {
        return jdbc.sql("UPDATE ingredients SET name = :n, base_unit = :u, min_quantity = :m, is_expiry_tracked = :e, is_active = :a WHERE id = :id")
                .param("id", id).param("n", name).param("u", baseUnit).param("m", minQuantity).param("e", expiryTracked)
                .param("a", active).update();
    }

    public List<KitchenTypeResponse> findTypes(Long ingredientId, boolean activeOnly) {
        return jdbc.sql(TYPE_SELECT + " WHERE (CAST(:g AS bigint) IS NULL OR t.raw_ingredient_id = :g) AND (t.is_active OR NOT :a) ORDER BY g.name, t.name")
                .param("g", ingredientId).param("a", activeOnly).query(KitchenTypeResponse.class).list();
    }

    public Optional<KitchenTypeResponse> findType(long id) {
        return jdbc.sql(TYPE_SELECT + " WHERE t.id = :id").param("id", id).query(KitchenTypeResponse.class).optional();
    }

    public long insertType(long rawIngredientId, String name, String handlingType, Integer shelfLifeHours, BigDecimal minimumQuantity,
                           boolean active) {
        return jdbc.sql("""
                        INSERT INTO kitchen_ingredient_types (raw_ingredient_id, name, handling_type, shelf_life_hours, minimum_quantity, is_active)
                        VALUES (:g, :n, :h, :s, :m, :a) RETURNING id""")
                .param("g", rawIngredientId).param("n", name).param("h", handlingType).param("s", shelfLifeHours)
                .param("m", minimumQuantity).param("a", active).query(Long.class).single();
    }

    public int updateType(long id, String name, Integer shelfLifeHours, BigDecimal minimumQuantity, boolean active) {
        return jdbc.sql("UPDATE kitchen_ingredient_types SET name = :n, shelf_life_hours = :s, minimum_quantity = :m, is_active = :a WHERE id = :id")
                .param("id", id).param("n", name).param("s", shelfLifeHours).param("m", minimumQuantity).param("a", active).update();
    }
}
