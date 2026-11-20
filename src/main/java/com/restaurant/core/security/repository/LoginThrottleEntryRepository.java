package com.restaurant.core.security.repository;

import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.restaurant.core.security.entity.LoginThrottleEntry;

public interface LoginThrottleEntryRepository extends JpaRepository<LoginThrottleEntry, String> {

    /**
     * Tạo dòng trống cho tên đăng nhập nếu chưa có. ON CONFLICT để 2 bản app cùng ghi lần sai đầu tiên không lỗi trùng
     * khóa; việc đếm vẫn do {@link LoginThrottleEntry#recordFailure} làm sau khi khóa dòng.
     */
    @Modifying
    @Query(value = "INSERT INTO login_throttles (username_key, failures, first_failure_at) VALUES (:usernameKey, 0, :now)"
            + " ON CONFLICT (username_key) DO NOTHING", nativeQuery = true)
    void insertIfAbsent(@Param("usernameKey") String usernameKey, @Param("now") Instant now);

    /** Khóa dòng (SELECT … FOR UPDATE) để 2 bản app ghi lần sai cùng lúc không đếm thiếu. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM LoginThrottleEntry e WHERE e.usernameKey = :usernameKey")
    Optional<LoginThrottleEntry> findByUsernameKeyForUpdate(@Param("usernameKey") String usernameKey);

    /** Dọn dòng đã hết khóa hoặc lần sai đầu đã quá cửa sổ đếm, để bảng không phình khi bị gửi hàng loạt tên ngẫu nhiên. */
    @Modifying
    @Query("DELETE FROM LoginThrottleEntry e WHERE e.lockedUntil <= :now"
            + " OR (e.lockedUntil IS NULL AND e.firstFailureAt < :windowStart)")
    int deleteExpired(@Param("now") Instant now, @Param("windowStart") Instant windowStart);
}
