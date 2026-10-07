package vn.khoibep.rms.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.khoibep.rms.entity.AuditEntry;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    /** Newest first, with the employee and the order's table in the same query. */
    @Query("""
            select a from AuditEntry a
            join fetch a.employee
            left join fetch a.order o
            left join fetch o.table
            where a.createdAt >= :from and a.createdAt < :to
            order by a.createdAt desc, a.id desc""")
    List<AuditEntry> findBetween(@Param("from") Instant from, @Param("to") Instant to);
}
