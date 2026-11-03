package com.restaurant.core.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.restaurant.core.inventory.entity.Ingredient;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {
}
