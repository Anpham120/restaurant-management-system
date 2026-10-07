package vn.khoibep.rms.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.khoibep.rms.entity.ShiftAssignment;

public interface ShiftAssignmentRepository extends JpaRepository<ShiftAssignment, Long> {

    @Query("""
            select a from ShiftAssignment a join fetch a.employee join fetch a.workShift s
            where a.workDate between :from and :to
            order by a.workDate, s.startTime, a.employee.fullName""")
    List<ShiftAssignment> findInRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select a from ShiftAssignment a join fetch a.employee join fetch a.workShift s
            where a.employee.id = :employeeId and a.workDate between :from and :to
            order by a.workDate, s.startTime""")
    List<ShiftAssignment> findForEmployee(@Param("employeeId") Long employeeId, @Param("from") LocalDate from,
                                          @Param("to") LocalDate to);

    @Query("""
            select a from ShiftAssignment a join fetch a.workShift s
            where a.employee.id = :employeeId and a.workDate = :day
            order by s.startTime""")
    List<ShiftAssignment> findForEmployeeOn(@Param("employeeId") Long employeeId, @Param("day") LocalDate day);

    boolean existsByWorkShiftId(Long workShiftId);

    /** Drops scheduled shifts in [from, to] that nobody clocked in to, e.g. for approved leave. */
    @Modifying
    @Query(value = """
            delete from shift_assignment a
            where a.employee_id = :employeeId and a.work_date between :from and :to
              and not exists (select 1 from attendance t where t.shift_assignment_id = a.id)""", nativeQuery = true)
    int deleteUnworked(@Param("employeeId") Long employeeId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Drops scheduled shifts after the given day that nobody clocked in to, e.g. once someone leaves. */
    @Modifying
    @Query(value = """
            delete from shift_assignment a
            where a.employee_id = :employeeId and a.work_date > :after
              and not exists (select 1 from attendance t where t.shift_assignment_id = a.id)""", nativeQuery = true)
    int deleteUnworkedAfter(@Param("employeeId") Long employeeId, @Param("after") LocalDate after);
}
