package vn.khoibep.rms.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.khoibep.rms.enums.Channel;
import vn.khoibep.rms.enums.OrderStatus;
import vn.khoibep.rms.model.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /** The order a booking opened (BR-42). */
    interface BookingOrder {
        Long getReservationId();

        Long getOrderId();
    }

    @Query("select o.reservation.id as reservationId, o.id as orderId from Order o where o.reservation.id in :reservationIds")
    List<BookingOrder> findByReservationIds(@Param("reservationIds") Collection<Long> reservationIds);

    /** BR-47: the order of this app code in its channel, cancelled ones aside. */
    Optional<Order> findFirstByChannelAndAppOrderCodeAndStatusNot(Channel channel, String appOrderCode,
                                                                  OrderStatus status);

    /** BR-44: a guest's visits, the latest first. */
    List<Order> findByCustomerIdAndStatusOrderByClosedAtDesc(Long customerId, OrderStatus status, Pageable page);

    @Query("select distinct o from Order o left join fetch o.table left join fetch o.items"
            + " where o.status = :status order by o.openedAt")
    List<Order> findWithItemsByStatus(@Param("status") OrderStatus status);

    @Query("select o from Order o left join fetch o.table left join fetch o.items where o.id = :id")
    Optional<Order> findWithItemsById(@Param("id") Long id);

    /** BR-04: the open order holding the table, whether as its main table or one put together with it. */
    @Query("select l.order from OrderTable l where l.table.id = :tableId and l.releasedAt is null")
    Optional<Order> findOpenByTableId(@Param("tableId") Long tableId);

    /** The same, by id only, so that a lock taken next reads the order fresh. */
    @Query("select l.order.id from OrderTable l where l.table.id = :tableId and l.releasedAt is null")
    Optional<Long> findOpenOrderIdByTableId(@Param("tableId") Long tableId);

    /** BR-18: whether any order, open or closed, ever held the table. */
    @Query("select count(l) > 0 from OrderTable l where l.table.id = :tableId")
    boolean tableEverHeld(@Param("tableId") Long tableId);

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
