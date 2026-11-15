package com.restaurant.core.common.validation;

import java.math.BigDecimal;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** null hợp lệ (để @NotNull quyết định). */
public class WholeVndValidator implements ConstraintValidator<WholeVnd, BigDecimal> {

    private static final BigDecimal MAX = new BigDecimal("9999999999");

    @Override
    public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return value.stripTrailingZeros().scale() <= 0 && value.abs().compareTo(MAX) <= 0;
    }
}
