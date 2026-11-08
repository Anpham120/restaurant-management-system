package com.restaurant.core.security.dto.response;

import java.util.List;

public record MeResponse(long employeeId, String username, String fullName, List<String> roles) {
}
