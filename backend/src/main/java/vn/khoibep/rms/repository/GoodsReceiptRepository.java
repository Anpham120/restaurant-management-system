package vn.khoibep.rms.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.khoibep.rms.entity.GoodsReceipt;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, Long> {

    /** Newest first, with the supplier, who entered it and every line, in one query. */
    @Query("""
            select distinct r from GoodsReceipt r
            join fetch r.supplier
            join fetch r.createdBy
            left join fetch r.lines l
            left join fetch l.item
            where r.createdAt >= :from and r.createdAt < :to
            order by r.createdAt desc""")
    List<GoodsReceipt> findBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select r from GoodsReceipt r
            join fetch r.supplier
            join fetch r.createdBy
            left join fetch r.lines l
            left join fetch l.item
            where r.id = :id""")
    Optional<GoodsReceipt> findWithLinesById(@Param("id") Long id);
}
