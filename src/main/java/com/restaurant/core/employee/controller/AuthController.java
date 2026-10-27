package com.restaurant.core.employee.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.restaurant.core.common.response.ApiResponse;
import com.restaurant.core.employee.dto.ChangePasswordRequest;
import com.restaurant.core.employee.dto.LoginRequest;
import com.restaurant.core.employee.dto.LoginResponse;
import com.restaurant.core.employee.dto.MeResponse;
import com.restaurant.core.employee.service.AuthService;
import com.restaurant.core.security.CurrentUser;

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

    /** Mọi người đã đăng nhập; bắt buộc khi LoginResponse.mustChangePassword = true. */
    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(currentUser.employeeId(), request);
        return ApiResponse.ok(null);
    }

    /** Mọi người đã đăng nhập. */
    @GetMapping("/me")
    public ApiResponse<MeResponse> me() {
        return ApiResponse.ok(authService.me(currentUser.employeeId(), currentUser.username(), currentUser.roles()));
    }
}
