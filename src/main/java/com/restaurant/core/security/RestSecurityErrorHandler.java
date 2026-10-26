package com.restaurant.core.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import com.restaurant.core.common.response.ApiError;
import com.restaurant.core.common.response.ApiResponse;

/** Trả 401/403 ở tầng filter với cùng định dạng JSON như GlobalExceptionHandler. */
@Component
public class RestSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper json;

    public RestSecurityErrorHandler(JsonMapper json) {
        this.json = json;
    }

    @Override
    public void commence(HttpServletRequest req, HttpServletResponse res, AuthenticationException ex) throws IOException {
        res.setHeader("WWW-Authenticate", "Bearer");
        write(res, HttpStatus.UNAUTHORIZED, ApiError.of("UNAUTHORIZED", "Chưa đăng nhập hoặc token không hợp lệ", req.getRequestURI()));
    }

    @Override
    public void handle(HttpServletRequest req, HttpServletResponse res, AccessDeniedException ex) throws IOException {
        write(res, HttpStatus.FORBIDDEN, ApiError.of("FORBIDDEN", "Không có quyền thực hiện thao tác này", req.getRequestURI()));
    }

    private void write(HttpServletResponse res, HttpStatus status, ApiError error) throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        json.writeValue(res.getOutputStream(), ApiResponse.fail(error));
    }
}
