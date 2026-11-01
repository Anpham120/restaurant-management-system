package com.restaurant.core.menu.dto;

import java.math.BigDecimal;

/**
 * Nguyên liệu bếp của một thực đơn ngày, theo base_unit của nguyên liệu gốc.
 * planned: Σ hạn mức × định lượng của các món dùng loại này; null khi có món không giới hạn suất (hasUnlimited).
 * used: tiêu hao thực tế khi nấu (sổ kho COOK) của các món trong thực đơn này.
 * onHandAtEnd: tồn bếp tính từ sổ kho tới cuối ngày kinh doanh (04:00 hôm sau), hoặc tới hiện tại nếu chưa hết ngày.
 */
public record IngredientUsageRow(long kitchenIngredientTypeId, String typeName, String baseUnit, BigDecimal planned,
                                 boolean hasUnlimited, BigDecimal used, BigDecimal onHandAtEnd) {
}
