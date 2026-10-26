package com.restaurant.core.employee.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 50) String username,
        @NotBlank @Size(max = 200) String password) {

    /** Không in mật khẩu khi record bị log/toString. */
    @Override
    public String toString() {
        return "LoginRequest[username=" + username + "]";
    }
}
