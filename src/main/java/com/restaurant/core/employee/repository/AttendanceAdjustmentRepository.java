package com.restaurant.core.employee.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.restaurant.core.employee.entity.AttendanceAdjustment;

public interface AttendanceAdjustmentRepository extends JpaRepository<AttendanceAdjustment, Long> {
}
