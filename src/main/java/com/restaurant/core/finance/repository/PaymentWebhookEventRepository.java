package com.restaurant.core.finance.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.restaurant.core.finance.entity.PaymentWebhookEvent;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, Long> {

    Optional<PaymentWebhookEvent> findByProviderTransactionCode(String providerTransactionCode);

    /** SELECT … FOR UPDATE: hai Quản lý xử lý cùng một sự cố thì chạy lần lượt, người sau thấy đã RESOLVED. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM PaymentWebhookEvent e WHERE e.id = :id")
    Optional<PaymentWebhookEvent> findByIdForUpdate(@Param("id") Long id);

    /**
     * Khóa theo mã giao dịch trong phạm vi transaction (pg_advisory_xact_lock): hai webhook cùng mã đến cùng lúc thì
     * xử lý lần lượt, lần sau thấy kết quả lần trước và không ghi trùng.
     */
    @Query(value = "SELECT CAST(pg_advisory_xact_lock(hashtext('payment_webhook_events'), hashtext(:code)) AS text)", nativeQuery = true)
    String lockTransactionCode(@Param("code") String transactionCode);
}
