package com.restaurant.core.employee.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.restaurant.core.employee.entity.Department;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
}
