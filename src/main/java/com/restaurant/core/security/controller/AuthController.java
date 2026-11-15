package com.restaurant.core.security.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.restaurant.core.common.response.ApiResponse;
import com.restaurant.core.security.CurrentUser;
import com.restaurant.core.security.dto.request.ChangePasswordRequest;
import com.restaurant.core.security.dto.request.LoginRequest;
import com.restaurant.core.security.dto.response.LoginResponse;
import com.restaurant.core.security.dto.response.MeResponse;
import com.restaurant.core.security.service.AuthService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CurrentUser currentUser;

    public AuthController(AuthService authService, CurrentUser currentUser) {
        this.authService = authService;
        this.currentUser = currentUser;
    }

    /** Công khai (SecurityConfig permitAll). */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    /**
     * Mọi người đã đăng nhập; bắt buộc khi LoginResponse.mustChangePassword = true.
     * Token đang dùng bị thu hồi: client phải thay bằng accessToken trong kết quả.
     */
    @PostMapping("/change-password")
    public ApiResponse<LoginResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return ApiResponse.ok(authService.changePassword(currentUser.employeeId(), request));
    }

    /** Mọi người đã đăng nhập. Thu hồi mọi token của tài khoản (đăng xuất trên mọi thiết bị). */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        authService.logout(currentUser.employeeId());
        return ApiResponse.ok(null);
    }

    /** Mọi người đã đăng nhập. */
    @GetMapping("/me")
    public ApiResponse<MeResponse> getCurrentAccount() {
        return ApiResponse.ok(authService.getCurrentAccount(currentUser.employeeId(), currentUser.username(), currentUser.roles()));
    }
}
