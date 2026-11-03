package com.restaurant.core.menu.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.restaurant.core.menu.entity.MenuCategory;

public interface MenuCategoryRepository extends JpaRepository<MenuCategory, Long> {
}
