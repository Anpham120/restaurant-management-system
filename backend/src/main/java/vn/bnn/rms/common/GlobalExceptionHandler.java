package vn.bnn.rms.common;

import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Maps errors to RFC 9457 problem details. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApi(ApiException ex) {
        return ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                detail.isEmpty() ? "Dữ liệu không hợp lệ" : detail);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail handleUnreadable(Exception ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Dữ liệu gửi lên không đúng định dạng");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này");
    }

    /** Last line of defence for rules enforced by the database (BR-04, BR-13, BR-14, BR-18). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleIntegrity(DataIntegrityViolationException ex) {
        String message = String.valueOf(ex.getMostSpecificCause().getMessage());
        String detail;
        if (message.contains("ux_orders_open_table")) {
            detail = "Bàn đã có đơn đang mở";
        } else if (message.contains("ux_payment_paid_order")) {
            detail = "Đơn đã được thanh toán";
        } else if (message.contains("ux_payment_pending_order")) {
            detail = "Đơn đang có yêu cầu chuyển khoản khác";
        } else if (message.contains("foreign key")) {
            detail = "Dữ liệu đang được sử dụng nên không xoá được";
        } else if (message.contains("duplicate key")) {
            detail = "Dữ liệu bị trùng";
        } else {
            detail = "Dữ liệu không hợp lệ";
        }
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail);
    }
}
