package com.restaurant.core.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.restaurant.core.inventory.entity.StockMovement;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
}
