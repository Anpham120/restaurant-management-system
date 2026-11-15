package com.restaurant.core.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Số tiền VND: số nguyên đồng, tối đa 10 chữ số. Nhận 10000 và 10000.00, từ chối 10000.50.
 * Lý do: ảnh QR và webhook ngân hàng chỉ có số nguyên đồng; phần lẻ làm hóa đơn không bao giờ khớp khi thanh toán QR.
 * Không dùng @Digits(fraction = 0) vì nó từ chối cả 10000.00 (scale 2).
 */
@Documented
@Constraint(validatedBy = WholeVndValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface WholeVnd {

    String message() default "phải là số tiền nguyên (VND không có phần lẻ), tối đa 10 chữ số";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
