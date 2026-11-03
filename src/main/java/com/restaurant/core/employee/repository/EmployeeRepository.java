package com.restaurant.core.employee.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.restaurant.core.employee.entity.Employee;
import com.restaurant.core.employee.entity.WorkStatus;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    /** Danh sách có phòng ban nạp sẵn (một câu SQL, không N+1). */
    @EntityGraph(attributePaths = "department")
    Page<Employee> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = "department")
    Page<Employee> findByWorkStatus(WorkStatus workStatus, Pageable pageable);

    @EntityGraph(attributePaths = "department")
    Optional<Employee> findWithDepartmentById(Long id);

    /** SELECT … FOR UPDATE: hai quản lý sửa / cho nghỉ cùng một người thì chạy lần lượt. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Employee e WHERE e.id = :id")
    Optional<Employee> findByIdForUpdate(@Param("id") Long id);
}
