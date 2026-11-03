package com.restaurant.core.inventory.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.restaurant.core.inventory.entity.StockCountItem;

public interface StockCountItemRepository extends JpaRepository<StockCountItem, Long> {

    List<StockCountItem> findByStockCountIdOrderById(Long stockCountId);
}
