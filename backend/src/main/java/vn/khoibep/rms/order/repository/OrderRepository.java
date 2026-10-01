package vn.khoibep.rms.order.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.khoibep.rms.order.entity.Order;
import vn.khoibep.rms.order.enums.OrderStatus;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("select distinct o from Order o left join fetch o.table left join fetch o.items"
            + " where o.status = :status order by o.openedAt")
    List<Order> findWithItemsByStatus(@Param("status") OrderStatus status);

    @Query("select o from Order o left join fetch o.table left join fetch o.items where o.id = :id")
    Optional<Order> findWithItemsById(@Param("id") Long id);

    Optional<Order> findByTableIdAndStatus(Long tableId, OrderStatus status);

    boolean existsByTableId(Long tableId);

    /** Row lock that serialises changes to one order: adding dishes, cancelling, paying. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.table.id = :tableId and o.status = :status")
    Optional<Order> findByTableIdAndStatusForUpdate(@Param("tableId") Long tableId,
                                                    @Param("status") OrderStatus status);

    /** Opens an order for a guest's first QR submission; two phones at once still get one order (BR-04). */
    @Modifying
    @Query(value = """
            insert into orders (type, status, table_id, opened_at)
            values ('DINE_IN', 'OPEN', :tableId, now())
            on conflict (table_id) where status = 'OPEN' do nothing
            """, nativeQuery = true)
    int insertOpenOrderIfAbsent(@Param("tableId") Long tableId);
}
