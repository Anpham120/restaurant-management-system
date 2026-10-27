package com.restaurant.core.menu.dto;

import java.math.BigDecimal;

/**
 * Món trong thực đơn ngày. dailyMenuItemId là khóa Order dùng khi gửi món. quotaUsed suy ra bằng rc_quota_used
 * (ACCEPTED trở đi, kể cả hủy sau khi nấu); remaining null khi không giới hạn suất.
 */
public record DailyMenuItemResponse(long dailyMenuItemId, long dailyMenuId, long menuItemId, String name, String categoryName,
                                    BigDecimal price, long recipeId, int recipeVersion, Integer quotaTotal, long quotaUsed,
                                    Long remaining, boolean paused, String pauseReason, boolean menuItemActive) {
}
