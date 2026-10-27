package com.restaurant.core.menu.dto;

import jakarta.validation.constraints.PositiveOrZero;

/** null = bỏ giới hạn suất. Không hạ dưới số suất đã chiếm (trigger DB → RC409). */
public record QuotaRequest(@PositiveOrZero Integer quotaTotal) {
}
