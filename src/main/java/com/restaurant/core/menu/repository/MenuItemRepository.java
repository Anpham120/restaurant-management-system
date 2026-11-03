package com.restaurant.core.menu.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.restaurant.core.menu.entity.MenuItem;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    /** Món đang kinh doanh, thuộc nhóm đang dùng, kèm id công thức hiện hành: [MenuItem, Long recipeId]. */
    @Query("""
            SELECT m, r.id FROM MenuItem m, Recipe r, MenuCategory c
             WHERE r.menuItemId = m.id AND r.current = true AND c.id = m.menuCategoryId AND m.active = true AND c.active = true""")
    List<Object[]> findSellableItemsWithCurrentRecipe();

    /** Khóa dòng món: hai lần tạo phiên bản công thức cùng lúc chạy lần lượt, không trùng số phiên bản. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM MenuItem m WHERE m.id = :id")
    Optional<MenuItem> findByIdForUpdate(@Param("id") Long id);
}
