package vn.khoibep.rms.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.khoibep.rms.entity.CashShift;

public interface CashShiftRepository extends JpaRepository<CashShift, Long> {

    @Query("""
            select s from CashShift s
            join fetch s.openedBy
            where s.closedAt is null""")
    Optional<CashShift> findOpen();

    /** Cash taken, paid out or counted waits here, so a shift being closed cannot miss any of it. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CashShift s where s.closedAt is null")
    Optional<CashShift> findOpenForUpdate();

    /** Newest first. */
    @Query("""
            select s from CashShift s
            join fetch s.openedBy
            left join fetch s.closedBy
            where s.openedAt >= :from and s.openedAt < :to
            order by s.openedAt desc""")
    List<CashShift> findOpenedBetween(@Param("from") Instant from, @Param("to") Instant to);
}
