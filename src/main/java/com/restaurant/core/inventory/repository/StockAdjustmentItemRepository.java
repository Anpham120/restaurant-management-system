package com.restaurant.core.inventory.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.restaurant.core.inventory.entity.StockAdjustmentItem;

public interface StockAdjustmentItemRepository extends JpaRepository<StockAdjustmentItem, Long> {

    List<StockAdjustmentItem> findByStockAdjustment_IdOrderById(Long stockAdjustmentId);
}
