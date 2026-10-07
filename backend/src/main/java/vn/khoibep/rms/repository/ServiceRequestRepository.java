package vn.khoibep.rms.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.khoibep.rms.enums.ServiceRequestType;
import vn.khoibep.rms.model.ServiceRequest;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, Long> {

    /**
     * BR-29: a second tap while the first call still waits adds nothing, even when both arrive at once. The time
     * comes from the application, like handledAt, so the response time is measured on one clock.
     */
    @Modifying
    @Query(value = """
            insert into service_request (table_id, type, created_at)
            values (:tableId, :type, :createdAt)
            on conflict (table_id, type) where handled_at is null do nothing
            """, nativeQuery = true)
    int insertOpenIfAbsent(@Param("tableId") Long tableId, @Param("type") String type,
                           @Param("createdAt") Instant createdAt);

    @Query("select r.type from ServiceRequest r where r.table.id = :tableId and r.handledAt is null order by r.type")
    List<ServiceRequestType> findOpenTypes(@Param("tableId") Long tableId);

    @Query("select r from ServiceRequest r join fetch r.table where r.handledAt is null order by r.createdAt")
    List<ServiceRequest> findOpen();

    /** Row lock, so two waiters cannot both take the same call. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ServiceRequest r where r.id = :id")
    Optional<ServiceRequest> findByIdForUpdate(@Param("id") Long id);
}
