package com.restaurant.core.order.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.restaurant.core.order.entity.CustomerOrder;

/**
 * Đơn luôn được khóa ĐẦU TIÊN trong mọi nghiệp vụ ghi (thứ tự khóa chung: đơn → hóa đơn → yêu cầu thanh toán →
 * phiên → bàn → món → lô bếp), để gọi món, chốt hóa đơn và thanh toán của cùng một bàn chạy lần lượt.
 */
public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {

    Optional<CustomerOrder> findByTableSessionId(Long tableSessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM CustomerOrder o WHERE o.id = :id")
    Optional<CustomerOrder> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM CustomerOrder o WHERE o.tableSessionId = :sessionId")
    Optional<CustomerOrder> findBySessionIdForUpdate(@Param("sessionId") Long sessionId);
}
