package com.restaurant.core.employee.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.restaurant.core.employee.entity.ShiftAssignment;

public interface ShiftAssignmentRepository extends JpaRepository<ShiftAssignment, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM ShiftAssignment a WHERE a.id = :id")
    Optional<ShiftAssignment> findByIdForUpdate(@Param("id") Long id);

    /** Nhân viên đã có ca (chưa hủy) chồng thời gian với khoảng [start, end). */
    @Query("""
            SELECT count(a) > 0 FROM ShiftAssignment a
             WHERE a.employee.id = :employeeId AND a.status <> 'CANCELLED'
               AND a.scheduledStartAt < :end AND a.scheduledEndAt > :start""")
    boolean existsOverlapping(@Param("employeeId") Long employeeId, @Param("start") Instant start, @Param("end") Instant end);

    /** Ca đã xếp sau một ngày, chưa chấm công (để hủy khi nhân viên nghỉ việc). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT a FROM ShiftAssignment a
             WHERE a.employee.id = :employeeId AND a.status = 'SCHEDULED' AND a.workDate > :afterDate
               AND NOT EXISTS (SELECT 1 FROM Attendance at WHERE at.shiftAssignment = a)
             ORDER BY a.id""")
    List<ShiftAssignment> findScheduledWithoutAttendanceAfter(@Param("employeeId") Long employeeId, @Param("afterDate") LocalDate afterDate);
}
