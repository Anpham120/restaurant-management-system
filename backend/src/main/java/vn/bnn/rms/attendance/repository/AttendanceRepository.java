package vn.bnn.rms.attendance.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.bnn.rms.attendance.entity.Attendance;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    Optional<Attendance> findByEmployeeIdAndCheckOutAtIsNull(Long employeeId);

    boolean existsByShiftAssignmentId(Long shiftAssignmentId);

    @Query("""
            select t from Attendance t join fetch t.employee left join fetch t.shiftAssignment a
            left join fetch a.workShift
            where t.checkInAt >= :from and t.checkInAt < :to
            order by t.checkInAt""")
    List<Attendance> findInRange(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select t from Attendance t join fetch t.employee left join fetch t.shiftAssignment a
            left join fetch a.workShift
            where t.employee.id = :employeeId and t.checkInAt >= :from and t.checkInAt < :to
            order by t.checkInAt""")
    List<Attendance> findInRangeFor(@Param("employeeId") Long employeeId, @Param("from") Instant from,
                                    @Param("to") Instant to);

    @Query("""
            select count(t) > 0 from Attendance t
            where t.employee.id = :employeeId and t.checkInAt >= :from and t.checkInAt < :to""")
    boolean existsFor(@Param("employeeId") Long employeeId, @Param("from") Instant from, @Param("to") Instant to);

    /** Clock-ins with a clock-out, for payroll (BR-26). */
    @Query("""
            select t from Attendance t join fetch t.employee
            where t.checkOutAt is not null and t.checkInAt >= :from and t.checkInAt < :to""")
    List<Attendance> findClosedBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select count(t) > 0 from Attendance t
            where t.checkOutAt is null and t.checkInAt >= :from and t.checkInAt < :to""")
    boolean existsOpenBetween(@Param("from") Instant from, @Param("to") Instant to);
}
