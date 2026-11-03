package com.restaurant.core.order.service;

import java.util.List;

import com.restaurant.core.order.dto.AcceptItemResponse;
import com.restaurant.core.order.dto.OrderItemResponse;

/** Luồng bếp: nhận món (kiểm hạn mức + giữ nguyên liệu), nấu (trừ kho), xong món. Owner: Phạm Duy An. */
public interface KitchenService {

    List<String> DEFAULT_QUEUE = List.of("PENDING", "ACCEPTED", "COOKING", "READY");

    List<OrderItemResponse> getKitchenQueue(List<String> statuses);

    /**
     * Nhận món: kiểm thực đơn còn bán, món không tạm dừng, còn suất, rồi giữ đủ nguyên liệu (FEFO).
     * Không đạt điều kiện nào thì món tự chuyển REJECTED kèm lý do (không ném lỗi).
     */
    AcceptItemResponse acceptOrderItem(long itemId, long actorId);

    OrderItemResponse rejectOrderItem(long itemId, long actorId, String reason);

    /** Bắt đầu nấu: trừ kho đúng phần nguyên liệu đã giữ. */
    OrderItemResponse startCookingItem(long itemId, long actorId);

    OrderItemResponse finishCookingItem(long itemId, long actorId);

    /** Lô nguyên liệu đang giữ bị hỏng/hết hạn: giữ lại từ lô khác. */
    OrderItemResponse reallocateItemIngredients(long itemId, long actorId);
}
