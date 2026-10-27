package com.restaurant.core.menu.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.restaurant.core.menu.dto.CategoryResponse;
import com.restaurant.core.menu.dto.MenuItemResponse;
import com.restaurant.core.menu.dto.RecipeResponse;

/**
 * Danh mục, món, công thức (bảng menu_categories, menu_items, recipes, recipe_ingredients — rc_app được ghi trực tiếp).
 * Trigger DB chặn sửa định lượng của công thức đã dùng trong thực đơn ngày → luôn tạo version mới. Owner: Lê Anh.
 */
@Repository
public class MenuCatalogRepository {

    private static final String ITEM_SELECT = """
            SELECT m.id, m.menu_category_id AS category_id, c.name AS category_name, m.name, m.price, m.image_url,
                   m.is_active AS active, r.version AS current_recipe_version
              FROM menu_items m
              JOIN menu_categories c ON c.id = m.menu_category_id
              LEFT JOIN recipes r ON r.menu_item_id = m.id AND r.is_current
            """;

    private final JdbcClient jdbc;

    public MenuCatalogRepository(JdbcClient jdbc) {
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

    public long insertCategory(String name, int sortOrder, boolean active) {
        return jdbc.sql("INSERT INTO menu_categories (name, sort_order, is_active) VALUES (:n, :s, :a) RETURNING id")
                .param("n", name).param("s", sortOrder).param("a", active).query(Long.class).single();
    }

    public int updateCategory(long id, String name, int sortOrder, boolean active) {
        return jdbc.sql("UPDATE menu_categories SET name = :n, sort_order = :s, is_active = :a WHERE id = :id")
                .param("id", id).param("n", name).param("s", sortOrder).param("a", active).update();
    }

    public List<MenuItemResponse> findItems(Long categoryId, boolean activeOnly) {
        return jdbc.sql(ITEM_SELECT + " WHERE (:cat::bigint IS NULL OR m.menu_category_id = :cat) AND (m.is_active OR NOT :activeOnly) ORDER BY c.sort_order, m.name")
                .param("cat", categoryId).param("activeOnly", activeOnly).query(MenuItemResponse.class).list();
    }

    public Optional<MenuItemResponse> findItem(long id) {
        return jdbc.sql(ITEM_SELECT + " WHERE m.id = :id").param("id", id).query(MenuItemResponse.class).optional();
    }

    public long insertItem(long categoryId, String name, BigDecimal price, String imageUrl, boolean active) {
        return jdbc.sql("INSERT INTO menu_items (menu_category_id, name, price, image_url, is_active) VALUES (:c, :n, :p, :i, :a) RETURNING id")
                .param("c", categoryId).param("n", name).param("p", price).param("i", imageUrl).param("a", active)
                .query(Long.class).single();
    }

    public int updateItem(long id, long categoryId, String name, BigDecimal price, String imageUrl, boolean active) {
        return jdbc.sql("UPDATE menu_items SET menu_category_id = :c, name = :n, price = :p, image_url = :i, is_active = :a WHERE id = :id")
                .param("id", id).param("c", categoryId).param("n", name).param("p", price).param("i", imageUrl).param("a", active)
                .update();
    }

    /** Khóa dòng món để tuần tự hóa hai lần tạo version đồng thời. Trả false nếu món không tồn tại. */
    public boolean lockItem(long menuItemId) {
        return jdbc.sql("SELECT id FROM menu_items WHERE id = :id FOR UPDATE").param("id", menuItemId).query(Long.class).optional().isPresent();
    }

    public int nextRecipeVersion(long menuItemId) {
        return jdbc.sql("SELECT COALESCE(max(version), 0) + 1 FROM recipes WHERE menu_item_id = :m").param("m", menuItemId)
                .query(Integer.class).single();
    }

    public void unsetCurrentRecipe(long menuItemId) {
        jdbc.sql("UPDATE recipes SET is_current = false WHERE menu_item_id = :m AND is_current").param("m", menuItemId).update();
    }

    public long insertRecipe(long menuItemId, int version) {
        return jdbc.sql("INSERT INTO recipes (menu_item_id, version, is_current) VALUES (:m, :v, true) RETURNING id")
                .param("m", menuItemId).param("v", version).query(Long.class).single();
    }

    public void insertRecipeIngredient(long recipeId, long kitchenTypeId, BigDecimal quantity) {
        jdbc.sql("INSERT INTO recipe_ingredients (recipe_id, kitchen_ingredient_type_id, quantity_per_serving) VALUES (:r, :t, :q)")
                .param("r", recipeId).param("t", kitchenTypeId).param("q", quantity).update();
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
