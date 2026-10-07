package vn.khoibep.rms.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.khoibep.rms.entity.Payslip;
import vn.khoibep.rms.enums.PayrollStatus;

public interface PayslipRepository extends JpaRepository<Payslip, Long> {

    @Query("select p from Payslip p join fetch p.employee e where p.payroll.id = :payrollId order by e.fullName")
    List<Payslip> findForPayroll(@Param("payrollId") Long payrollId);

    @Query("""
            select p from Payslip p join fetch p.payroll r
            where p.employee.id = :employeeId and r.status = :status
            order by r.period desc""")
    List<Payslip> findForEmployee(@Param("employeeId") Long employeeId, @Param("status") PayrollStatus status);

    /** Rows of [payroll id, payslip count, total net pay], for the list of months. */
    @Query("select p.payroll.id, count(p), coalesce(sum(p.netAmount), 0) from Payslip p group by p.payroll.id")
    List<Object[]> totalsByPayroll();
}
