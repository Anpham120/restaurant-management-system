package com.restaurant.core.menu.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** status: DRAFT → PUBLISHED → CLOSED. Waiter chỉ gửi được món của thực đơn PUBLISHED đúng ngày kinh doanh. */
public record DailyMenuResponse(long id, LocalDate businessDate, String status, Instant publishedAt, List<DailyMenuItemResponse> items) {

    public record Header(long id, LocalDate businessDate, String status, Instant publishedAt) {
    }
}
