package com.restaurant.core.inventory.repository;

import java.util.Collection;
import java.util.List;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.restaurant.core.inventory.entity.StockIssueItem;

/** Dòng xuất cũng được khóa theo id tăng dần. */
public interface StockIssueItemRepository extends JpaRepository<StockIssueItem, Long> {

    List<StockIssueItem> findByStockIssueIdOrderById(Long stockIssueId);

    /** Lô kho chính của các dòng xuất (đọc id, không nạp dòng — dòng sẽ được khóa sau). */
    @Query("SELECT DISTINCT l.inventoryBatchId FROM StockIssueItem l WHERE l.id IN :ids")
    List<Long> findInventoryBatchIdsOfIssueItems(@Param("ids") Collection<Long> issueItemIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM StockIssueItem l WHERE l.id IN :ids ORDER BY l.id")
    List<StockIssueItem> lockIssueItemsByIds(@Param("ids") Collection<Long> ids);
}
