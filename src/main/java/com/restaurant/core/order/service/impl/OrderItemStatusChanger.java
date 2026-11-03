package com.restaurant.core.order.service.impl;

import java.util.Set;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import com.restaurant.core.common.exception.ApiException;
import com.restaurant.core.common.time.BusinessDateProvider;
import com.restaurant.core.order.entity.OrderItem;
import com.restaurant.core.order.entity.OrderItemEvent;
import com.restaurant.core.order.entity.OrderItemStatus;
import com.restaurant.core.order.repository.OrderItemEventRepository;
import com.restaurant.core.order.repository.OrderItemRepository;

/** Nơi DUY NHẤT đổi trạng thái món: kiểm bước chuyển hợp lệ và ghi nhật ký order_item_events. */
@Component
@RequiredArgsConstructor
public class OrderItemStatusChanger {

    private final OrderItemRepository orderItemRepository;
    private final OrderItemEventRepository orderItemEventRepository;
    private final BusinessDateProvider clock;

    /** Ghi nhật ký cho món vừa tạo (null → PENDING). */
    public void recordItemCreated(OrderItem item, long actorId) {
        orderItemEventRepository.save(new OrderItemEvent(item.getId(), actorId, null, OrderItemStatus.PENDING, null, clock.now()));
    }

    /**
     * Đổi trạng thái món nếu món đang ở một trong {@code allowedFrom}. Món phải đã được khóa (…ForUpdate).
     *
     * @return trạng thái cũ
     */
    public OrderItemStatus changeItemStatus(OrderItem item, Set<OrderItemStatus> allowedFrom, OrderItemStatus next, long actorId,
                                            String reason) {
        OrderItemStatus previous = item.getStatus();
        if (!allowedFrom.contains(previous) || !previous.canChangeTo(next)) {
            throw ApiException.invalidState("Món " + item.getId() + " đang " + previous + ": không chuyển sang " + next + " được");
        }
        item.changeStatus(next);
        orderItemRepository.flush();    // ghi trạng thái mới TRƯỚC nhật ký: DB kiểm nhật ký khớp trạng thái hiện tại của món
        orderItemEventRepository.save(new OrderItemEvent(item.getId(), actorId, previous, next, reason, clock.now()));
        return previous;
    }
}
