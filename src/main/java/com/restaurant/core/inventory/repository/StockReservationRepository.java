package com.restaurant.core.inventory.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.restaurant.core.inventory.entity.ReservationStatus;
import com.restaurant.core.inventory.entity.StockReservation;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    /** Mọi lần giữ của một món (kể cả đã trả / đã nấu), xếp theo lô. */
    @Query("SELECT r FROM StockReservation r JOIN FETCH r.kitchenBatch WHERE r.orderItemId = :itemId ORDER BY r.kitchenBatch.id")
    List<StockReservation> findAllOfOrderItem(@Param("itemId") long orderItemId);

    @Query("""
            SELECT r FROM StockReservation r JOIN FETCH r.kitchenBatch
             WHERE r.orderItemId = :itemId AND r.status = com.restaurant.core.inventory.entity.ReservationStatus.ACTIVE
             ORDER BY r.kitchenBatch.id""")
    List<StockReservation> findActiveOfOrderItem(@Param("itemId") long orderItemId);

    /** Chỉ lấy id lô (không nạp entity) để khóa lô TRƯỚC khi nạp — tránh Hibernate giữ số liệu lô cũ chưa khóa. */
    @Query("""
            SELECT r.kitchenBatch.id FROM StockReservation r
             WHERE r.orderItemId = :itemId AND r.status = com.restaurant.core.inventory.entity.ReservationStatus.ACTIVE""")
    List<Long> findActiveBatchIdsOfOrderItem(@Param("itemId") long orderItemId);

    @Query("SELECT DISTINCT r.orderItemId FROM StockReservation r WHERE r.kitchenBatch.id = :batchId AND r.status = :status ORDER BY r.orderItemId")
    List<Long> findOrderItemIdsHoldingBatch(@Param("batchId") long kitchenBatchId, @Param("status") ReservationStatus status);
}
