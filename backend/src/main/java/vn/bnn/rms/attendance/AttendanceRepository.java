package vn.bnn.rms.attendance;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
