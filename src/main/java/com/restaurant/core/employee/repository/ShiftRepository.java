package com.restaurant.core.employee.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.restaurant.core.employee.entity.Shift;

public interface ShiftRepository extends JpaRepository<Shift, Long> {
}
