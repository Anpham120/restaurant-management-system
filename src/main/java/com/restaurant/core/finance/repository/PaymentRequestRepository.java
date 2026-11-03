package com.restaurant.core.finance.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.restaurant.core.finance.entity.PaymentRequest;
import com.restaurant.core.finance.entity.PaymentRequestStatus;

public interface PaymentRequestRepository extends JpaRepository<PaymentRequest, Long> {

    @Query("SELECT r.invoiceId FROM PaymentRequest r WHERE r.paymentReference = :reference")
    Optional<Long> findInvoiceIdByReference(@Param("reference") String paymentReference);

    @Query("SELECT r.invoiceId FROM PaymentRequest r WHERE r.id = :id")
    Optional<Long> findInvoiceIdOfRequest(@Param("id") Long requestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM PaymentRequest r WHERE r.paymentReference = :reference")
    Optional<PaymentRequest> findByPaymentReferenceForUpdate(@Param("reference") String paymentReference);

    long countByInvoiceId(Long invoiceId);

    /** Khóa các yêu cầu thanh toán đang mở của hóa đơn, theo id tăng dần. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM PaymentRequest r WHERE r.invoiceId = :invoiceId AND r.status IN :statuses ORDER BY r.id")
    List<PaymentRequest> findByInvoiceIdAndStatusInForUpdate(@Param("invoiceId") Long invoiceId,
                                                             @Param("statuses") Collection<PaymentRequestStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM PaymentRequest r WHERE r.id = :id")
    Optional<PaymentRequest> findByIdForUpdate(@Param("id") Long id);
}
