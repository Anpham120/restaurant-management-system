package vn.bnn.rms.payroll.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.bnn.rms.payroll.entity.Payroll;
import vn.bnn.rms.payroll.enums.PayrollStatus;

public interface PayrollRepository extends JpaRepository<Payroll, Long> {

    List<Payroll> findAllByOrderByPeriodDesc();

    boolean existsByPeriod(String period);

    boolean existsByPeriodAndStatus(String period, PayrollStatus status);
}
