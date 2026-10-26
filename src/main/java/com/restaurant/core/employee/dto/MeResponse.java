package com.restaurant.core.employee.dto;

import java.util.List;

public record MeResponse(long employeeId, String username, String fullName, List<String> roles) {
}
