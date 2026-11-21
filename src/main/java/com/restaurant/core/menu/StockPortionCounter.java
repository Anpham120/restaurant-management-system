package com.restaurant.core.menu;

import java.util.Collection;
import java.util.Map;

/** Số suất theo tồn bếp cho màn hình thực đơn. Module Kho cài đặt. */
public interface StockPortionCounter {

    /** Món có công thức rỗng không có trong kết quả. */
    Map<Long, Integer> countAvailablePortions(Collection<Long> dailyMenuItemIds);
}
