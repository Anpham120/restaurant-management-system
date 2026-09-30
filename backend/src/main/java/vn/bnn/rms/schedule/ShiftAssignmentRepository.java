package vn.bnn.rms.schedule;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
