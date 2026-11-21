package com.restaurant.core.menu.dto.response;

import java.math.BigDecimal;

/**
 * Món trong thực đơn ngày. dailyMenuItemId là khóa Order dùng khi gửi món. quotaUsed do QuotaUsageCounter đếm
 * (ACCEPTED trở đi, kể cả hủy sau khi nấu); remaining null khi không giới hạn suất.
 * stockRemaining là số suất theo tồn bếp hiện tại, null nếu công thức không cần nguyên liệu.
 */
public record DailyMenuItemResponse(long dailyMenuItemId, long dailyMenuId, long menuItemId, String name, String categoryName,
                                    BigDecimal price, long recipeId, int recipeVersion, Integer quotaTotal, long quotaUsed,
                                    Long remaining, Integer stockRemaining, boolean paused, String pauseReason, boolean menuItemActive) {
}
