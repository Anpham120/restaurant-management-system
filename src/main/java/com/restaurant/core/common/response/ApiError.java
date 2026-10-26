package com.restaurant.core.common.response;

import java.time.Instant;
import java.util.List;

/**
 * Chi tiết lỗi. {@code code} là mã máy đọc được: RC001/RC403/RC409/RC422/RC500 lấy nguyên từ PostgreSQL,
 * hoặc mã của backend như VALIDATION_ERROR, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, INTERNAL_ERROR.
 */
public record ApiError(String code, String message, List<FieldViolation> details, String path, Instant timestamp) {

    public record FieldViolation(String field, String message) {
    }

    public static ApiError of(String code, String message, String path) {
        return new ApiError(code, message, List.of(), path, Instant.now());
    }
}
