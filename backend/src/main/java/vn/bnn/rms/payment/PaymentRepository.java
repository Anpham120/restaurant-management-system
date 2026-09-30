package vn.bnn.rms.payment;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByReference(String reference);

    boolean existsByReference(String reference);

    Optional<Payment> findByOrderIdAndStatus(Long orderId, PaymentStatus status);

    @Query("select p.order.id from Payment p where p.reference = :reference")
    Optional<Long> findOrderIdByReference(@Param("reference") String reference);

    @Modifying
    @Query("update Payment p set p.status = :to where p.order.id = :orderId and p.status = :from")
    int updateStatusByOrderId(@Param("orderId") Long orderId,
                              @Param("from") PaymentStatus from,
                              @Param("to") PaymentStatus to);
}
