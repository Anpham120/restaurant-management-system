package com.restaurant.core.security.entity;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Số lần đăng nhập sai của một tên đăng nhập (bảng login_throttles), dùng chung cho mọi bản app đang chạy.
 * Dòng được tạo trống (failures = 0) bằng {@code LoginThrottleEntryRepository.insertIfAbsent}, sau đó chỉ đổi qua
 * {@link #recordFailure}.
 */
@Entity
@Table(name = "login_throttles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoginThrottleEntry {

    @Id
    @Column(name = "username_key", length = 50)
    private String usernameKey;

    @Column(name = "failures", nullable = false)
    private int failures;

    @Column(name = "first_failure_at", nullable = false)
    private Instant firstFailureAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    /** Số giây còn bị khóa tại thời điểm {@code now}; 0 = được thử đăng nhập. */
    public long lockedSecondsAt(Instant now) {
        if (lockedUntil == null) {
            return 0;
        }
        return Math.max(0, Duration.between(now, lockedUntil).toSeconds());
    }

    /** Hết khóa, hoặc chưa bị khóa mà lần sai đầu đã quá cửa sổ đếm: lần sai tiếp theo đếm lại từ đầu. */
    public boolean isExpiredAt(Instant now, Duration window) {
        return lockedUntil != null ? !now.isBefore(lockedUntil) : now.isAfter(firstFailureAt.plus(window));
    }

    /** Ghi thêm một lần sai; đủ {@code maxFailures} lần trong {@code window} thì khóa tạm {@code lock}. */
    public void recordFailure(Instant now, int maxFailures, Duration window, Duration lock) {
        if (failures == 0 || isExpiredAt(now, window)) {
            failures = 0;
            firstFailureAt = now;
            lockedUntil = null;
        }
        failures++;
        if (failures >= maxFailures) {
            lockedUntil = now.plus(lock);
        }
    }
}
