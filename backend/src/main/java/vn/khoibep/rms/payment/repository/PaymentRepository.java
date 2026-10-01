package vn.khoibep.rms.payment.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.khoibep.rms.payment.entity.Payment;
import vn.khoibep.rms.payment.enums.PaymentStatus;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** The cash one shift took: how much, and in how many payments (BR-39). */
    interface CashTaken {
        Long getShiftId();

        Long getAmount();

        Long getPayments();
    }

    /** Only cash ever belongs to a shift. */
    @Query("""
            select p.cashShift.id as shiftId, sum(p.amount) as amount, count(p) as payments
            from Payment p
            where p.cashShift.id in :shiftIds
            group by p.cashShift.id""")
    List<CashTaken> cashTakenIn(@Param("shiftIds") Collection<Long> shiftIds);

    Optional<Payment> findByReference(String reference);

    boolean existsByReference(String reference);

    Optional<Payment> findByOrderIdAndStatus(Long orderId, PaymentStatus status);

    /** BR-43: an order may have several payments taken. */
    List<Payment> findByOrderIdAndStatusOrderByPaidAtAscIdAsc(Long orderId, PaymentStatus status);

    @Query("select p.order.id from Payment p where p.reference = :reference")
    Optional<Long> findOrderIdByReference(@Param("reference") String reference);

    @Modifying
    @Query("update Payment p set p.status = :to where p.order.id = :orderId and p.status = :from")
    int updateStatusByOrderId(@Param("orderId") Long orderId,
                              @Param("from") PaymentStatus from,
                              @Param("to") PaymentStatus to);
}
