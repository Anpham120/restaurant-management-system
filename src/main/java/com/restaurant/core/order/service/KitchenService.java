package com.restaurant.core.order.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.restaurant.core.order.dto.AcceptItemResponse;
import com.restaurant.core.order.dto.OrderItemResponse;
import com.restaurant.core.order.repository.OrderRepository;

/**
 * Luồng bếp. Quota, đặt giữ và tiêu hao nguyên liệu (FEFO) nằm trong rc_accept_item / rc_start_cooking — Java chỉ gọi hàm
 * và đọc lại trạng thái.
 */
@Service
public class KitchenService {

    public static final List<String> DEFAULT_QUEUE = List.of("PENDING", "ACCEPTED", "COOKING", "READY");

    private final OrderRepository orders;
    private final OrderService orderService;

    public KitchenService(OrderRepository orders, OrderService orderService) {
        this.orders = orders;
        this.orderService = orderService;
    }

    public List<OrderItemResponse> queue(List<String> statuses) {
        return orders.findKitchenQueue(statuses == null || statuses.isEmpty() ? DEFAULT_QUEUE : statuses);
    }

    public AcceptItemResponse accept(long itemId, long actorId) {
        String result = orders.acceptItem(itemId, actorId);
        String reason = "REJECTED".equals(result) ? orders.findRejectReason(itemId).orElse(null) : null;
        return new AcceptItemResponse(result, reason, orderService.item(itemId));
    }

    public OrderItemResponse reject(long itemId, long actorId, String reason) {
        orders.rejectItem(itemId, actorId, reason);
        return orderService.item(itemId);
    }

    public OrderItemResponse startCooking(long itemId, long actorId) {
        orders.startCooking(itemId, actorId);
        return orderService.item(itemId);
    }

    public OrderItemResponse finish(long itemId, long actorId) {
        orders.finishDish(itemId, actorId);
        return orderService.item(itemId);
    }

    /** Cấp lại nguyên liệu khi lô đang giữ bị đánh dấu hỏng/hết hạn (has_stock_exception). */
    public OrderItemResponse reallocate(long itemId, long actorId) {
        orders.reallocateItem(itemId, actorId);
        return orderService.item(itemId);
    }
}
