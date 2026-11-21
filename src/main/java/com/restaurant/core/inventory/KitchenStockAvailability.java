package com.restaurant.core.inventory;

import java.util.List;

/** Tồn bếp có thể dùng để gọi món, sau khi tính cả nhu cầu của các món đang chờ bếp nhận. */
public interface KitchenStockAvailability {

    record DishPortions(long dailyMenuItemId, String name, int quantity) {
    }

    /** Kiểm tra toàn bộ lượt gọi trong cùng transaction sẽ lưu đơn. */
    void requireAvailableForBatch(List<DishPortions> dishes);
}
