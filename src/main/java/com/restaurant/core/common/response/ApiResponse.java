package com.restaurant.core.common.response;

/**
 * Định dạng phản hồi thống nhất cho mọi API.
 * Thành công: {"success": true, "data": ...}; lỗi: {"success": false, "error": {...}}.
 */
public record ApiResponse<T>(boolean success, T data, ApiError error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> fail(ApiError error) {
        return new ApiResponse<>(false, null, error);
    }
}
