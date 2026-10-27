package com.restaurant.core.employee.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 72 = giới hạn byte của BCrypt. */
public record ChangePasswordRequest(@NotBlank @Size(max = 72) String currentPassword, @NotBlank @Size(min = 8, max = 72) String newPassword) {
}
