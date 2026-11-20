package com.restaurant.core.security.service;

/**
 * Chống dò mật khẩu theo tên đăng nhập: sai 5 lần trong 15 phút thì khóa tạm 15 phút. Áp dụng cho mọi tên (kể cả không
 * tồn tại) nên không lộ tài khoản nào có thật. Bộ đếm lưu trong database nên mọi bản app dùng chung và không mất khi
 * khởi động lại. Giới hạn theo IP đặt ở nginx (limit_req), xem docs/devops/PIPELINE.md.
 */
public interface LoginThrottleService {

    /** Số giây còn bị khóa; 0 = được thử đăng nhập. */
    long lockedSeconds(String username);

    void recordFailure(String username);

    void recordSuccess(String username);
}
