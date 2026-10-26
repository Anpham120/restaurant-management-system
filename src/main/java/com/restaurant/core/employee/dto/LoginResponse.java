package com.restaurant.core.employee.dto;

import java.time.Instant;
import java.util.List;

public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, long employeeId, String username,
                            List<String> roles, boolean mustChangePassword) {
}
