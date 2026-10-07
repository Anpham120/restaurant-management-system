package vn.khoibep.rms.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.khoibep.rms.model.Reservation;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    boolean existsByCode(String code);

    /** BR-44: a guest's bookings, the latest first. */
    List<Reservation> findByCustomerIdOrderByReservedAtDesc(Long customerId, Pageable page);

    /** By time of arrival. */
    @Query("""
            select r from Reservation r
            left join fetch r.table
            where r.reservedAt >= :from and r.reservedAt < :to
            order by r.reservedAt, r.id""")
    List<Reservation> findBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") Long id);

    /** The webhook finds a deposit by the code in the transfer content (BR-42). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.code = :code")
    Optional<Reservation> findByCodeForUpdate(@Param("code") String code);

    /** Deposits received and not yet taken off a bill: money held for guests (BR-42). */
    @Query("select coalesce(sum(r.depositAmount), 0) from Reservation r where r.depositPaidAt is not null and r.depositApplied is null")
    long depositsHeld();
}
