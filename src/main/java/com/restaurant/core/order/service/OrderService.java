package com.restaurant.core.order.service;

import java.util.List;

import com.restaurant.core.order.dto.response.ItemEventResponse;
import com.restaurant.core.order.dto.response.OrderItemResponse;
import com.restaurant.core.order.dto.response.OrderResponse;
import com.restaurant.core.order.dto.request.SubmitBatchRequest;
import com.restaurant.core.order.dto.response.SubmitBatchResponse;

/** Đơn hàng phía phục vụ: gọi món, phục vụ, hủy món. Phía bếp: {@link KitchenService}. Owner: Phạm Duy An. */
public interface OrderService {

    OrderResponse getOrderById(long orderId);

    OrderItemResponse getOrderItemById(long itemId);

    List<ItemEventResponse> getOrderItemHistory(long itemId);

    /**
     * Gửi một lượt món xuống bếp. Gửi lặp cùng requestKey → trả lượt cũ (replayed), không tạo món trùng.
     * Món vượt hạn mức chỉ bị CẢNH BÁO, bếp kiểm cứng khi nhận món.
     */
    SubmitBatchResponse submitOrderBatch(long orderId, long actorId, SubmitBatchRequest request);

    /** Món đã xong → đã bưng ra cho khách. */
    OrderItemResponse markItemServed(long itemId, long actorId);

    /** Hủy món chưa nấu (chờ bếp / đã nhận): trả lại nguyên liệu đã giữ. */
    OrderItemResponse cancelItemBeforeCooking(long itemId, long actorId, String reason);

    /** Quản lý hủy món đã nấu / đã phục vụ (ngoại lệ): không thu tiền món đó, nguyên liệu đã dùng không hoàn lại. */
    OrderItemResponse cancelItemAfterCooking(long itemId, long actorId, String reason);
}
