package com.restaurant.core.common.exception;

import org.springframework.http.HttpStatus;

/** Lỗi nghiệp vụ do backend chủ động ném ra (ví dụ: không tìm thấy, sai thông tin đăng nhập). */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
