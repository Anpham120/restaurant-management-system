package vn.bnn.rms.leave.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.bnn.rms.leave.entity.LeaveRequest;
import vn.bnn.rms.leave.enums.LeaveStatus;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    @Query("select l from LeaveRequest l join fetch l.employee order by l.createdAt desc")
    List<LeaveRequest> findAllWithEmployee();

    @Query("select l from LeaveRequest l join fetch l.employee where l.status = :status order by l.fromDate")
    List<LeaveRequest> findByStatusWithEmployee(@Param("status") LeaveStatus status);

    @Query("""
            select l from LeaveRequest l join fetch l.employee
            where l.employee.id = :employeeId order by l.fromDate desc""")
    List<LeaveRequest> findForEmployee(@Param("employeeId") Long employeeId);

    /** Does this person have a request in one of these states that overlaps [from, to]? */
    @Query("""
            select count(l) > 0 from LeaveRequest l
            where l.employee.id = :employeeId and l.status in :statuses and l.fromDate <= :to and l.toDate >= :from""")
    boolean existsOverlapping(@Param("employeeId") Long employeeId, @Param("from") LocalDate from,
                              @Param("to") LocalDate to, @Param("statuses") Collection<LeaveStatus> statuses);

    /** Everyone's requests in one state that overlap [from, to], e.g. approved leave for payroll. */
    @Query("""
            select l from LeaveRequest l join fetch l.employee
            where l.status = :status and l.fromDate <= :to and l.toDate >= :from""")
    List<LeaveRequest> findOverlapping(@Param("status") LeaveStatus status, @Param("from") LocalDate from,
                                       @Param("to") LocalDate to);
}
