package com.restaurant.core.employee.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DepartmentRequest(@NotBlank @Size(max = 50) String name) {
}
