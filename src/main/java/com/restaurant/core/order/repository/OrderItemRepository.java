package com.restaurant.core.order.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.restaurant.core.order.entity.OrderItem;
import com.restaurant.core.order.entity.OrderItemStatus;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM OrderItem i JOIN FETCH i.batch WHERE i.id = :id")
    Optional<OrderItem> findByIdForUpdate(@Param("id") Long id);

    /** Khóa mọi món của đơn theo id tăng dần. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM OrderItem i WHERE i.batch.orderId = :orderId ORDER BY i.id")
    List<OrderItem> findAllOfOrderForUpdate(@Param("orderId") Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM OrderItem i WHERE i.id IN :ids ORDER BY i.id")
    List<OrderItem> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    @Query("SELECT i FROM OrderItem i JOIN FETCH i.batch WHERE i.batch.orderId = :orderId ORDER BY i.id")
    List<OrderItem> findAllOfOrder(@Param("orderId") Long orderId);

    boolean existsByDailyMenuItemId(Long dailyMenuItemId);

    @Query("SELECT i FROM OrderItem i WHERE i.batch.id = :batchId ORDER BY i.id")
    List<OrderItem> findAllOfBatch(@Param("batchId") Long batchId);

    @Query("SELECT count(i) > 0 FROM OrderItem i WHERE i.batch.orderId = :orderId AND i.status NOT IN :statuses")
    boolean existsInOrderWithStatusNotIn(@Param("orderId") Long orderId, @Param("statuses") Collection<OrderItemStatus> statuses);

    /** Món đã chiếm suất: đang ở trạng thái dùng suất, hoặc bị hủy sau khi đã bắt đầu nấu. */
    @Query("""
            SELECT i.dailyMenuItemId, COALESCE(SUM(i.quantity), 0) FROM OrderItem i
             WHERE i.dailyMenuItemId IN :dmiIds
               AND (i.status IN :usingQuota
                    OR (i.status = com.restaurant.core.order.entity.OrderItemStatus.CANCELLED
                        AND EXISTS (SELECT 1 FROM OrderItemEvent e
                                     WHERE e.orderItemId = i.id
                                       AND e.toStatus = com.restaurant.core.order.entity.OrderItemStatus.COOKING)))
             GROUP BY i.dailyMenuItemId""")
    List<Object[]> sumUsedPortions(@Param("dmiIds") Collection<Long> dailyMenuItemIds,
                                   @Param("usingQuota") Collection<OrderItemStatus> usingQuota);
}
