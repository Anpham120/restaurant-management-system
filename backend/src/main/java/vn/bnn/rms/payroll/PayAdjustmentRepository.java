package vn.bnn.rms.payroll;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayAdjustmentRepository extends JpaRepository<PayAdjustment, Long> {

    List<PayAdjustment> findByPayslipIdInOrderByCreatedAtAscIdAsc(Collection<Long> payslipIds);

    @Query("select coalesce(sum(a.amount), 0) from PayAdjustment a where a.payslip.id = :payslipId")
    long sumForPayslip(@Param("payslipId") Long payslipId);
}
