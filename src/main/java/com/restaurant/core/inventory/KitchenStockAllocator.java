package com.restaurant.core.inventory;

import java.util.Optional;

/**
 * API của module Kho cho module Gọi món: giữ, trả và tiêu hao nguyên liệu bếp cho một món.
 * Nguyên liệu lấy theo FEFO: lô hết hạn sớm nhất dùng trước. Cài đặt: {@code inventory.service.impl.KitchenStockAllocatorImpl}.
 * Mọi method phải chạy trong transaction của người gọi.
 */
public interface KitchenStockAllocator {

    /**
     * Giữ đủ nguyên liệu cho món (số suất × định lượng công thức). Thiếu bất kỳ loại nào thì không giữ gì cả.
     *
     * @return rỗng nếu giữ thành công; ngược lại là lý do thiếu nguyên liệu (để từ chối món)
     */
    Optional<String> reserveIngredientsForItem(long orderItemId, long dailyMenuItemId, int servings);

    /** Trả toàn bộ phần đang giữ của món (món bị hủy). */
    void releaseReservationsOfItem(long orderItemId);

    /** Bắt đầu nấu: trừ kho đúng phần đã giữ và ghi sổ kho. Lô giữ đã hỏng/hết hạn → lỗi, phải cấp lại trước. */
    void consumeReservationsForCooking(long orderItemId);

    /**
     * Cấp lại nguyên liệu khi lô đang giữ bị đánh dấu hỏng hoặc hết hạn: trả phần ở lô hỏng rồi giữ lại từ lô khác.
     * Vẫn thiếu → lỗi INSUFFICIENT_STOCK và không thay đổi gì.
     */
    void reallocateUnusableReservations(long orderItemId, long dailyMenuItemId, int servings);
}
