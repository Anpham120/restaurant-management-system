package vn.khoibep.rms.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.khoibep.rms.entity.OrderItem;
import vn.khoibep.rms.enums.ItemStatus;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Query("select i from OrderItem i join fetch i.order o left join fetch o.table"
            + " where i.status in :statuses order by i.sentAt, i.id")
    List<OrderItem> findKitchenItems(@Param("statuses") Collection<ItemStatus> statuses);

    @Query("select i.order.id from OrderItem i where i.id = :id")
    Optional<Long> findOrderIdById(@Param("id") Long id);

    boolean existsByMenuItemId(Long menuItemId);
}
