package com.restaurant.core.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Lỗi nghiệp vụ do Service chủ động ném ra. GlobalExceptionHandler chuyển thành ApiResponse lỗi.
 * Dùng các hàm tạo có tên bên dưới để mã lỗi thống nhất trong toàn hệ thống.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    /** 404: không tìm thấy bản ghi. */
    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    /** 409: bản ghi đang ở trạng thái không cho phép thao tác này (ví dụ món đã nấu thì không hủy thường được). */
    public static ApiException invalidState(String message) {
        return new ApiException(HttpStatus.CONFLICT, "INVALID_STATE", message);
    }

    /** 422: dữ liệu gửi lên hợp lệ về định dạng nhưng sai về nghiệp vụ (thiếu lý do, số lượng âm…). */
    public static ApiException invalidRequest(String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_REQUEST", message);
    }

    /** 409: kho không đủ nguyên liệu. */
    public static ApiException insufficientStock(String message) {
        return new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", message);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
