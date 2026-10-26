package com.restaurant.core.common.exception;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.restaurant.core.common.response.ApiError;
import com.restaurant.core.common.response.ApiResponse;

/** Một chỗ duy nhất chuyển exception thành {@link ApiResponse} lỗi. Controller/Service không tự bắt lỗi để trả JSON. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final PostgresErrorTranslator translator;

    public GlobalExceptionHandler(PostgresErrorTranslator translator) {
        this.translator = translator;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiResponse<Void>> handleApi(ApiException ex, HttpServletRequest req) {
        return error(ex.status(), ex.code(), ex.getMessage(), List.of(), req);
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiResponse<Void>> handleDatabase(DataAccessException ex, HttpServletRequest req) {
        PostgresErrorTranslator.Translated t = translator.translate(ex);
        if (t == null) {
            log.error("Lỗi database không phân loại tại {}", req.getRequestURI(), ex);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Lỗi hệ thống", List.of(), req);
        }
        if (t.status().is5xxServerError()) {
            log.error("Lỗi database {} tại {}", t.code(), req.getRequestURI(), ex);
        } else {
            log.info("Từ chối nghiệp vụ {} tại {}: {}", t.code(), req.getRequestURI(), t.message());
        }
        return error(t.status(), t.code(), t.message(), List.of(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> handleBodyValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<ApiError.FieldViolation> details = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> new ApiError.FieldViolation(f.getField(), f.getDefaultMessage()))
                .toList();
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Dữ liệu gửi lên không hợp lệ", details, req);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiResponse<Void>> handleParamValidation(HandlerMethodValidationException ex, HttpServletRequest req) {
        // Body (@Valid @RequestBody) → lỗi theo từng field; tham số đơn (@PathVariable, @RequestParam) → tên tham số
        List<ApiError.FieldViolation> details = ex.getParameterValidationResults().stream()
                .flatMap(r -> r instanceof ParameterErrors errors
                        ? errors.getFieldErrors().stream().map(f -> new ApiError.FieldViolation(f.getField(), f.getDefaultMessage()))
                        : r.getResolvableErrors().stream()
                                .map(e -> new ApiError.FieldViolation(r.getMethodParameter().getParameterName(), e.getDefaultMessage())))
                .toList();
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Tham số không hợp lệ", details, req);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex, HttpServletRequest req) {
        List<ApiError.FieldViolation> details = ex.getConstraintViolations().stream()
                .map(v -> new ApiError.FieldViolation(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Tham số không hợp lệ", details, req);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiResponse<Void>> handleUnreadable(Exception ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Yêu cầu sai định dạng", List.of(), req);
    }

    /** @PreAuthorize từ chối → 403 (chưa đăng nhập thì Spring Security trả 401 trước khi tới đây). */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<Void>> handleDenied(AccessDeniedException ex, HttpServletRequest req) {
        return error(HttpStatus.FORBIDDEN, "FORBIDDEN", "Không có quyền thực hiện thao tác này", List.of(), req);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy đường dẫn", List.of(), req);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiResponse<Void>> handleMethod(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Phương thức HTTP không được hỗ trợ", List.of(), req);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> handleOther(Exception ex, HttpServletRequest req) {
        log.error("Lỗi không lường trước tại {}", req.getRequestURI(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Lỗi hệ thống", List.of(), req);
    }

    private static ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String code, String message,
                                                           List<ApiError.FieldViolation> details, HttpServletRequest req) {
        ApiError err = new ApiError(code, message, details, req.getRequestURI(), java.time.Instant.now());
        return ResponseEntity.status(status).body(ApiResponse.fail(err));
    }
}
