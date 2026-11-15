package com.restaurant.core.employee.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * Thu hồi mọi JWT đã phát cho tài khoản (claim "tv" sẽ khác token_version). Tăng bằng một câu UPDATE trong DB, không
     * qua entity: Account không ánh xạ cột này, nên các lần lưu Account khác không bao giờ ghi đè giá trị đã tăng.
     */
    @Modifying
    @Query(value = "UPDATE accounts SET token_version = token_version + 1 WHERE id = :id", nativeQuery = true)
    int revokeTokens(@Param("id") Long id);
}
