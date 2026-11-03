package com.restaurant.core.employee.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** Vai trò là role_code: MANAGER, WAITER, KITCHEN, CASHIER. */
public record RolesRequest(@NotEmpty @Size(max = 4) List<@NotBlank String> roles) {
}
