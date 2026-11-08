package com.restaurant.core.menu.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/**
 * Ngày kinh doanh (mốc 04:00 giờ Việt Nam — docs/07 §11). fromCatalog = true: đưa sẵn mọi món đang kinh doanh có công
 * thức hiện hành vào thực đơn với hạn mức 0 (chưa mở bán); quản lý nhập số suất cho món bán trong ngày.
 * Bỏ trống = false (thực đơn trống như trước).
 */
public record DailyMenuRequest(@NotNull LocalDate businessDate, Boolean fromCatalog) {
}
