package com.restaurant.core.menu;

import java.util.Collection;
import java.util.Map;

/**
 * Số suất đã dùng của món trong thực đơn ngày. Module Thực đơn cần số này (hiển thị, điền hạn mức) nhưng dữ liệu nằm ở
 * module Gọi món, nên Thực đơn chỉ khai báo interface; module Gọi món cài đặt (đảo phụ thuộc, tránh vòng).
 * Một suất "đã dùng" khi món đã được bếp nhận (ACCEPTED, COOKING, READY, SERVED) hoặc bị hủy sau khi đã nấu.
 */
public interface QuotaUsageCounter {

    long countUsedPortions(long dailyMenuItemId);

    /** Kết quả có đủ mọi id được hỏi (id không có món nào → 0). */
    Map<Long, Long> countUsedPortions(Collection<Long> dailyMenuItemIds);

    /** Món trong thực đơn ngày đã từng được gọi (bất kể trạng thái) — khi đó không xóa / đổi món được. */
    boolean hasBeenOrdered(long dailyMenuItemId);
}
