package com.restaurant.core.finance.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.restaurant.core.finance.entity.Invoice;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByOrderId(Long orderId);

    /** Chỉ lấy id đơn (không nạp hóa đơn) để khóa đơn TRƯỚC rồi mới khóa và nạp hóa đơn. */
    @Query("SELECT i.orderId FROM Invoice i WHERE i.id = :id")
    Optional<Long> findOrderIdOfInvoice(@Param("id") Long invoiceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Invoice i WHERE i.orderId = :orderId")
    Optional<Invoice> findByOrderIdForUpdate(@Param("orderId") Long orderId);
}
