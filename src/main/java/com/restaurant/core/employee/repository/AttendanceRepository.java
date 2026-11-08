package com.restaurant.core.employee.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.restaurant.core.employee.entity.Attendance;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    boolean existsByShiftAssignment_Id(Long shiftAssignmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Attendance a WHERE a.id = :id")
    Optional<Attendance> findByIdForUpdate(@Param("id") Long id);
}
