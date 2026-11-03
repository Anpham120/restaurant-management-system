package com.restaurant.core.employee.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

public record LeaveRequest(@NotNull LocalDate endDate) {
}
