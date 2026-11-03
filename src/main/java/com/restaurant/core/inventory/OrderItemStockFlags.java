package com.restaurant.core.inventory;

import java.util.Collection;

/**
 * Module Kho cần đánh dấu "lô nguyên liệu đang giữ bị hỏng" lên món của module Gọi món. Kho chỉ khai báo interface,
 * module Gọi món cài đặt (đảo phụ thuộc, tránh vòng Kho ↔ Gọi món).
 */
public interface OrderItemStockFlags {

    /** Khóa các món (SELECT … FOR UPDATE, theo id tăng dần) trước khi khóa lô — đúng thứ tự khóa chung. */
    void lockOrderItems(Collection<Long> orderItemIds);

    /** Bật cờ has_stock_exception: món phải được cấp lại nguyên liệu hoặc hủy trước khi nấu. */
    void markStockException(Collection<Long> orderItemIds);
}
