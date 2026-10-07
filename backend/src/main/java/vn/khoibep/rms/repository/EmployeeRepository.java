package vn.khoibep.rms.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.khoibep.rms.entity.Employee;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByUsername(String username);

    boolean existsByUsername(String username);
}
