package com.restaurant.core.menu;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * API của module Thực đơn cho module khác (gọi món, kho bếp): tra món đang bán và công thức của nó.
 * Cài đặt: {@code menu.service.impl.DailyMenuLookupImpl}.
 */
public interface DailyMenuLookup {

    /** Thông tin món trong thực đơn ngày tại thời điểm gọi món / nhận món. */
    record OrderableDish(long dailyMenuItemId, String name, BigDecimal price, boolean menuItemActive, boolean onSaleToday,
                         boolean paused, String pauseReason, Integer quotaTotal, long recipeId) {
    }

    /** Một dòng công thức: loại nguyên liệu bếp và lượng cho một suất. */
    record RecipeLine(long kitchenIngredientTypeId, BigDecimal quantityPerServing) {
    }

    Optional<OrderableDish> findDish(long dailyMenuItemId);

    /** Như {@link #findDish} nhưng khóa dòng món (SELECT … FOR UPDATE) để kiểm hạn mức an toàn khi nhận món đồng thời. */
    Optional<OrderableDish> findDishForUpdate(long dailyMenuItemId);

    /** Công thức áp dụng cho món trong thực đơn ngày, xếp theo loại nguyên liệu. */
    List<RecipeLine> getRecipeLines(long dailyMenuItemId);
}
