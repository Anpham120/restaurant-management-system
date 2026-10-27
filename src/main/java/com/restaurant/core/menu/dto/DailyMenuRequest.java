package com.restaurant.core.menu.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/** Ngày kinh doanh (mốc 04:00 giờ Việt Nam — docs/07 §11). */
public record DailyMenuRequest(@NotNull LocalDate businessDate) {
}
