package com.restaurant.core.menu.dto;

import java.time.LocalDate;

/** updated: số món được điền hạn mức = số suất đã bán ở thực đơn sourceDate. */
public record FillQuotasResponse(LocalDate sourceDate, int updated, DailyMenuResponse menu) {
}
