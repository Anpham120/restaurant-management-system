package com.restaurant.core.menu.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.restaurant.core.menu.dto.CategoryResponse;
import com.restaurant.core.menu.dto.MenuItemResponse;
import com.restaurant.core.menu.dto.RecipeResponse;

/** Truy vấn CHỈ ĐỌC danh mục, món, công thức (ghép nhiều bảng, trả thẳng DTO). Ghi: các JpaRepository. Owner: Lê Anh. */
@Repository
public class MenuCatalogQueryRepository {

    private static final String ITEM_SELECT = """
            SELECT m.id, m.menu_category_id AS category_id, c.name AS category_name, m.name, m.price, m.image_url,
                   m.is_active AS active, r.version AS current_recipe_version
              FROM menu_items m
              JOIN menu_categories c ON c.id = m.menu_category_id
              LEFT JOIN recipes r ON r.menu_item_id = m.id AND r.is_current
            """;

    private final JdbcClient jdbc;

    public MenuCatalogQueryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<CategoryResponse> findCategories(boolean activeOnly) {
        return jdbc.sql("SELECT id, name, sort_order, is_active AS active FROM menu_categories WHERE is_active OR NOT :activeOnly ORDER BY sort_order, name")
                .param("activeOnly", activeOnly).query(CategoryResponse.class).list();
    }

    public Optional<CategoryResponse> findCategory(long id) {
        return jdbc.sql("SELECT id, name, sort_order, is_active AS active FROM menu_categories WHERE id = :id")
                .param("id", id).query(CategoryResponse.class).optional();
    }



    public List<MenuItemResponse> findItems(Long categoryId, boolean activeOnly) {
        return jdbc.sql(ITEM_SELECT + " WHERE (:cat::bigint IS NULL OR m.menu_category_id = :cat) AND (m.is_active OR NOT :activeOnly) ORDER BY c.sort_order, m.name")
                .param("cat", categoryId).param("activeOnly", activeOnly).query(MenuItemResponse.class).list();
    }

    public Optional<MenuItemResponse> findItem(long id) {
        return jdbc.sql(ITEM_SELECT + " WHERE m.id = :id").param("id", id).query(MenuItemResponse.class).optional();
    }








    public record RecipeHeader(long id, long menuItemId, int version, boolean current) {
    }

    public List<RecipeHeader> findRecipes(long menuItemId) {
        return jdbc.sql("SELECT id, menu_item_id, version, is_current AS current FROM recipes WHERE menu_item_id = :m ORDER BY version DESC")
                .param("m", menuItemId).query(RecipeHeader.class).list();
    }

    public List<RecipeResponse.Line> findRecipeLines(long recipeId) {
        return jdbc.sql("""
                        SELECT ri.kitchen_ingredient_type_id, t.name AS type_name, g.base_unit, ri.quantity_per_serving
                          FROM recipe_ingredients ri
                          JOIN kitchen_ingredient_types t ON t.id = ri.kitchen_ingredient_type_id
                          JOIN ingredients g ON g.id = t.raw_ingredient_id
                         WHERE ri.recipe_id = :r ORDER BY t.name
                        """)
                .param("r", recipeId).query(RecipeResponse.Line.class).list();
    }
}
