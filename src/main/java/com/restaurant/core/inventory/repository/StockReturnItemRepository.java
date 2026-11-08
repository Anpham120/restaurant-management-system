package com.restaurant.core.inventory.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.restaurant.core.inventory.entity.StockReturnItem;

public interface StockReturnItemRepository extends JpaRepository<StockReturnItem, Long> {

    List<StockReturnItem> findByStockReturn_IdOrderById(Long stockReturnId);
}
