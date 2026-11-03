package com.restaurant.core.security.dto;

import java.util.List;

public record MeResponse(long employeeId, String username, String fullName, List<String> roles) {
}
