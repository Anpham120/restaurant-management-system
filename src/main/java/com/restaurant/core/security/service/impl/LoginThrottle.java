package com.restaurant.core.security.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Chống dò mật khẩu theo tên đăng nhập: sai {@link #MAX_FAILURES} lần trong {@link #WINDOW} thì khóa tạm {@link #LOCK}.
 * Áp dụng cho mọi tên (kể cả không tồn tại) nên không lộ tài khoản nào có thật. Lưu trong bộ nhớ, không đổi schema DB;
 * khởi động lại app thì xóa. Giới hạn theo IP đặt ở nginx (limit_req), xem docs/devops/PIPELINE.md.
 */
@Component
public class LoginThrottle {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);
    static final Duration LOCK = Duration.ofMinutes(15);
    /** Chặn việc gửi hàng loạt tên ngẫu nhiên làm phình bộ nhớ: quá ngưỡng thì dọn các mục đã hết hạn. */
    private static final int MAX_TRACKED = 10_000;

    private record Attempts(int failures, Instant firstFailure, Instant lockedUntil) {
    }

    private final ConcurrentHashMap<String, Attempts> byUser = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginThrottle() {
        this(Clock.systemUTC());
    }

    LoginThrottle(Clock clock) {
        this.clock = clock;
    }

    /** Số giây còn bị khóa; 0 = được thử đăng nhập. */
    public long lockedSeconds(String username) {
        Attempts a = byUser.get(throttleKeyOf(username));
        if (a == null || a.lockedUntil() == null) {
            return 0;
        }
        return Math.max(0, Duration.between(clock.instant(), a.lockedUntil()).toSeconds());
    }

    public void recordFailure(String username) {
        Instant now = clock.instant();
        if (byUser.size() >= MAX_TRACKED) {
            byUser.entrySet().removeIf(e -> expired(e.getValue(), now));
        }
        byUser.compute(throttleKeyOf(username), (k, a) -> {
            if (a == null || expired(a, now)) {
                a = new Attempts(0, now, null);
            }
            int n = a.failures() + 1;
            return new Attempts(n, a.firstFailure(), n >= MAX_FAILURES ? now.plus(LOCK) : null);
        });
    }

    public void recordSuccess(String username) {
        byUser.remove(throttleKeyOf(username));
    }

    /** Hết khóa, hoặc chưa bị khóa mà lần sai đầu đã quá cửa sổ đếm. */
    private static boolean expired(Attempts a, Instant now) {
        return a.lockedUntil() != null ? !now.isBefore(a.lockedUntil()) : now.isAfter(a.firstFailure().plus(WINDOW));
    }

    private static String throttleKeyOf(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
