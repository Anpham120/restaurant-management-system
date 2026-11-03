package com.restaurant.core.employee.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.restaurant.core.employee.entity.Account;

public interface AccountRepository extends JpaRepository<Account, Long> {

    @EntityGraph(attributePaths = "roles")
    Optional<Account> findByEmployeeId(Long employeeId);

    /** Tài khoản + vai trò của cả một trang nhân viên trong một câu SQL. */
    @EntityGraph(attributePaths = "roles")
    List<Account> findByEmployeeIdIn(Collection<Long> employeeIds);

    /** SELECT … FOR UPDATE: khóa / mở khóa / đổi vai trò cùng lúc thì chạy lần lượt. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);
}
