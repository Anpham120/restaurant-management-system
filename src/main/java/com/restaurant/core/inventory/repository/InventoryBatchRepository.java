package com.restaurant.core.inventory.repository;

import java.util.Collection;
import java.util.List;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.restaurant.core.inventory.entity.InventoryBatch;

/** Khóa lô kho chính luôn theo id tăng dần (thứ tự khóa chung). */
public interface InventoryBatchRepository extends JpaRepository<InventoryBatch, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM InventoryBatch b WHERE b.id IN :ids ORDER BY b.id")
    List<InventoryBatch> lockBatchesByIds(@Param("ids") Collection<Long> ids);

    /** Khóa mọi lô của các nguyên liệu cho trước (cần cho kiểm tra FEFO khi xuất kho). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT b FROM InventoryBatch b
             WHERE b.stockReceiptItem.id IN (SELECT s.id FROM StockReceiptItem s WHERE s.ingredient.id IN :ingredientIds)
             ORDER BY b.id""")
    List<InventoryBatch> lockBatchesOfIngredients(@Param("ingredientIds") Collection<Long> ingredientIds);
}
