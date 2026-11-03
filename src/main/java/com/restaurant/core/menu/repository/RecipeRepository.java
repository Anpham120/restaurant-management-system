package com.restaurant.core.menu.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.restaurant.core.menu.entity.Recipe;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    @Query("SELECT COALESCE(MAX(r.version), 0) FROM Recipe r WHERE r.menuItemId = :menuItemId")
    int findLatestVersion(@Param("menuItemId") Long menuItemId);

    Optional<Recipe> findByMenuItemIdAndCurrentTrue(Long menuItemId);
}
