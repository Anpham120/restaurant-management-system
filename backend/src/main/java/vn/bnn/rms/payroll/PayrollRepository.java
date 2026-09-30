package vn.bnn.rms.payroll;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollRepository extends JpaRepository<Payroll, Long> {

    List<Payroll> findAllByOrderByPeriodDesc();

    boolean existsByPeriod(String period);

    boolean existsByPeriodAndStatus(String period, PayrollStatus status);
}
