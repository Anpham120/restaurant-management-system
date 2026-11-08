package com.restaurant.core.inventory.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.restaurant.core.inventory.entity.StockReceiptItem;

public interface StockReceiptItemRepository extends JpaRepository<StockReceiptItem, Long> {

    List<StockReceiptItem> findByStockReceipt_IdOrderById(Long stockReceiptId);

    /** Nguyên liệu của các lô kho chính (đọc id, không nạp lô — lô sẽ được khóa sau). */
    @Query("""
            SELECT DISTINCT s.ingredient.id FROM StockReceiptItem s
             WHERE s.id IN (SELECT b.stockReceiptItem.id FROM InventoryBatch b WHERE b.id IN :batchIds)""")
    List<Long> findIngredientIdsOfBatches(@Param("batchIds") Collection<Long> inventoryBatchIds);
}
