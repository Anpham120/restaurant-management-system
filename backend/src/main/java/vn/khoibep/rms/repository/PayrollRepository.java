package vn.khoibep.rms.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.khoibep.rms.entity.Payroll;
import vn.khoibep.rms.enums.PayrollStatus;

public interface PayrollRepository extends JpaRepository<Payroll, Long> {

    List<Payroll> findAllByOrderByPeriodDesc();

    boolean existsByPeriod(String period);

    boolean existsByPeriodAndStatus(String period, PayrollStatus status);
}
