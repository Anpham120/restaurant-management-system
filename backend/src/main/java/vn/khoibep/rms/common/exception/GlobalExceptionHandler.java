package vn.khoibep.rms.common.exception;

import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
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

    /** BR-30, BR-31: also says in Retry-After when to try again. */
    @ExceptionHandler(TooManyRequestsException.class)
    ResponseEntity<ProblemDetail> handleTooManyRequests(TooManyRequestsException ex) {
        return ResponseEntity.status(ex.getStatus())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage()));
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
        if (message.contains("ux_orders_open_table") || message.contains("ux_order_table_active")) {
            detail = "Bàn đã có đơn đang mở";
        } else if (message.contains("ux_payment_paid_order")) {
            detail = "Đơn đã được thanh toán";
        } else if (message.contains("ux_payment_pending_order")) {
            detail = "Đơn đang có yêu cầu chuyển khoản khác";
        } else if (message.contains("ux_shift_assignment")) {
            detail = "Nhân viên đã được xếp ca này trong ngày";
        } else if (message.contains("ux_attendance_open")) {
            detail = "Bạn đang trong ca";
        } else if (message.contains("ux_attendance_assignment")) {
            detail = "Ca này đã chấm công";
        } else if (message.contains("attendance_shift_assignment_id_fkey")) {
            detail = "Ca đã có chấm công nên không gỡ được";
        } else if (message.contains("payroll_period_key")) {
            detail = "Tháng này đã có bảng lương";
        } else if (message.contains("ck_payslip_net")) {
            detail = "Thực nhận không được âm";
        } else if (message.contains("foreign key")) {
            detail = "Dữ liệu đang được sử dụng nên không xoá được";
        } else if (message.contains("ux_adjustment_comp_item")) {
            detail = "Món này đã được tặng";
        } else if (message.contains("duplicate key")) {
            detail = "Dữ liệu bị trùng";
        } else {
            detail = "Dữ liệu không hợp lệ";
        }
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail);
    }
}
