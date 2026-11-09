package com.restaurant.core.common.util;

import java.math.BigDecimal;

/** Chuẩn hóa chuỗi và số để lưu hoặc ghi vào thông báo lỗi. Dùng chung cho mọi module. */
public final class TextUtils {

    private TextUtils() {
    }

    /** Chuỗi rỗng hoặc chỉ có khoảng trắng thành null; còn lại bỏ khoảng trắng hai đầu. */
    public static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    /** Số dạng dễ đọc trong thông báo: bỏ số 0 thừa sau dấu thập phân, không dùng ký hiệu khoa học. */
    public static String toPlainText(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
