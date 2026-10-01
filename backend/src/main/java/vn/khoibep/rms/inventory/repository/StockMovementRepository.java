package vn.khoibep.rms.inventory.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.khoibep.rms.inventory.entity.StockMovement;
import vn.khoibep.rms.inventory.enums.MovementType;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    /** The net change of one ingredient for one type of movement. */
    interface Total {
        Long getInventoryItemId();

        MovementType getType();

        BigDecimal getQuantityChange();
    }

    List<StockMovement> findByItemIdOrderByCreatedAtDescIdDesc(Long itemId);

    /** BR-38: what a dish in an order took from each ingredient, net of anything it already gave back. */
    @Query("""
            select m.item.id as inventoryItemId, m.type as type, sum(m.quantityChange) as quantityChange
            from StockMovement m
            where m.orderItemId = :orderItemId
            group by m.item.id, m.type""")
    List<Total> totalsForDish(@Param("orderItemId") Long orderItemId);

    /** FR-09.10: the net change of each ingredient by type between two instants. */
    @Query("""
            select m.item.id as inventoryItemId, m.type as type, sum(m.quantityChange) as quantityChange
            from StockMovement m
            where m.createdAt >= :from and m.createdAt < :to and m.type in :types
            group by m.item.id, m.type""")
    List<Total> totalsBetween(@Param("from") Instant from, @Param("to") Instant to,
                              @Param("types") Collection<MovementType> types);
}
