package com.restaurant.core.security.service;

import java.util.List;

import com.restaurant.core.security.service.impl.LoginThrottle;
import com.restaurant.core.security.dto.request.ChangePasswordRequest;
import com.restaurant.core.security.dto.request.LoginRequest;
import com.restaurant.core.security.dto.response.LoginResponse;
import com.restaurant.core.security.dto.response.MeResponse;

public interface AuthService {

    /** Sai quá {@link LoginThrottle} cho phép thì từ chối (429) trước cả khi kiểm mật khẩu, kể cả mật khẩu đúng. */
    LoginResponse login(LoginRequest request);

    /** Kiểm mật khẩu cũ, lưu hash mới, tắt cờ bắt buộc đổi. Token đang dùng vẫn hợp lệ tới khi hết hạn. */
    void changePassword(long employeeId, ChangePasswordRequest request);

    MeResponse getCurrentAccount(long employeeId, String username, List<String> roles);
}
