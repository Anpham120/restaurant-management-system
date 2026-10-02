package vn.khoibep.rms.einvoice.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.khoibep.rms.einvoice.entity.EInvoice;

public interface EInvoiceRepository extends JpaRepository<EInvoice, Long> {

    Optional<EInvoice> findByOrderId(Long orderId);

    /** The queue of some days, oldest first. */
    @Query("""
            select i from EInvoice i left join fetch i.issuedBy
            where i.invoiceDate >= :from and i.invoiceDate < :to
            order by i.invoiceDate, i.id""")
    List<EInvoice> findBetween(@Param("from") Instant from, @Param("to") Instant to);

    /** FR-20.4: what an export takes, with the lines, oldest first. */
    @Query("""
            select i from EInvoice i left join fetch i.lines
            where i.invoiceDate >= :from and i.invoiceDate < :to and i.invoiceNo is null
            order by i.invoiceDate, i.id""")
    List<EInvoice> findNotIssuedWithLines(@Param("from") Instant from, @Param("to") Instant to);

    /** Serialises changes to one invoice: the buyer, the number. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from EInvoice i where i.id = :id")
    Optional<EInvoice> findByIdForUpdate(@Param("id") Long id);

    boolean existsByInvoiceSymbolAndInvoiceNoAndIdNot(String invoiceSymbol, String invoiceNo, Long id);
}
