package vn.khoibep.rms.common.util;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import vn.khoibep.rms.common.exception.ApiException;

/** Checks date ranges that come from query parameters. */
public final class DateRange {

    private DateRange() {
    }

    /** Rejects a reversed range, or one longer than {@code maxDays}, so a typo cannot load years of data. */
    public static void check(LocalDate from, LocalDate to, int maxDays) {
        if (from.isAfter(to)) {
            throw ApiException.badRequest("Ngày bắt đầu phải trước ngày kết thúc");
        }
        if (ChronoUnit.DAYS.between(from, to) >= maxDays) {
            throw ApiException.badRequest("Chỉ xem được tối đa " + maxDays + " ngày mỗi lần");
        }
    }
}
