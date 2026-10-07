package vn.khoibep.rms.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.khoibep.rms.enums.AdjustmentStatus;
import vn.khoibep.rms.model.Adjustment;

public interface AdjustmentRepository extends JpaRepository<Adjustment, Long> {

    /** Oldest first, with the order, its table, the dish and who asked, for the manager's list. */
    @Query("""
            select a from Adjustment a
            join fetch a.order o
            left join fetch o.table
            left join fetch a.item
            join fetch a.createdBy
            where a.status = :status
            order by a.createdAt, a.id""")
    List<Adjustment> findWithStatus(@Param("status") AdjustmentStatus status);

    @Query("select a.order.id from Adjustment a where a.id = :id")
    Optional<Long> findOrderIdById(@Param("id") Long id);
}
