package com.restaurant.core.security.service;

import java.util.List;

import com.restaurant.core.security.dto.request.ChangePasswordRequest;
import com.restaurant.core.security.dto.request.LoginRequest;
import com.restaurant.core.security.dto.response.LoginResponse;
import com.restaurant.core.security.dto.response.MeResponse;

public interface AuthService {

    /** Sai quá {@link LoginThrottleService} cho phép thì từ chối (429) trước cả khi kiểm mật khẩu, kể cả mật khẩu đúng. */
    LoginResponse login(LoginRequest request);

    /**
     * Kiểm mật khẩu cũ, lưu hash mới, tắt cờ bắt buộc đổi. Mọi token cũ (kể cả token đang dùng) hết hiệu lực;
     * trả token mới để người đang thao tác không bị đăng xuất.
     */
    LoginResponse changePassword(long employeeId, ChangePasswordRequest request);

    /** Đăng xuất phía server: thu hồi mọi token của tài khoản trên mọi thiết bị. */
    void logout(long employeeId);

    MeResponse getCurrentAccount(long employeeId, String username, List<String> roles);
}
