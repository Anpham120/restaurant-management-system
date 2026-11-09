package com.restaurant.core.order.service.impl;

import java.util.EnumSet;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.restaurant.core.common.exception.ApiException;
import com.restaurant.core.inventory.KitchenStockAllocator;
import com.restaurant.core.menu.DailyMenuLookup.OrderableDish;
import com.restaurant.core.menu.DailyMenuLookup;
import com.restaurant.core.menu.QuotaUsageCounter;
import com.restaurant.core.order.dto.response.AcceptItemResponse;
import com.restaurant.core.order.dto.response.OrderItemResponse;
import com.restaurant.core.order.entity.OrderItem;
import com.restaurant.core.order.entity.OrderItemStatus;
import com.restaurant.core.order.repository.OrderItemRepository;
import com.restaurant.core.order.repository.OrderQueryRepository;
import com.restaurant.core.order.service.KitchenService;
import com.restaurant.core.order.service.OrderService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KitchenServiceImpl implements KitchenService {

    private final OrderItemRepository orderItemRepository;
    private final OrderQueryRepository orderQueryRepository;
    private final OrderItemStatusChanger statusChanger;
    private final DailyMenuLookup dailyMenuLookup;
    private final QuotaUsageCounter quotaUsageCounter;
    private final KitchenStockAllocator kitchenStockAllocator;
    private final OrderService orderService;

    @Override
    public List<OrderItemResponse> getKitchenQueue(List<String> statuses) {
        return orderQueryRepository.findKitchenQueue(statuses == null || statuses.isEmpty() ? DEFAULT_QUEUE : statuses);
    }

    @Override
    @Transactional
    public AcceptItemResponse acceptOrderItem(long itemId, long actorId) {
        OrderItem item = findItemForUpdate(itemId);
        if (item.getStatus() != OrderItemStatus.PENDING) {
            throw ApiException.invalidState("Món " + itemId + " đang " + item.getStatus() + ", không phải chờ bếp nhận");
        }
        String rejectReason = findReasonDishCannotBeServed(item);
        if (rejectReason == null) {
            rejectReason = kitchenStockAllocator.reserveIngredientsForItem(itemId, item.getDailyMenuItemId(), item.getQuantity())
                    .orElse(null);
        }
        if (rejectReason != null) {
            statusChanger.changeItemStatus(item, EnumSet.of(OrderItemStatus.PENDING), OrderItemStatus.REJECTED, actorId, rejectReason);
            return new AcceptItemResponse("REJECTED", rejectReason, orderService.getOrderItemById(itemId));
        }
        statusChanger.changeItemStatus(item, EnumSet.of(OrderItemStatus.PENDING), OrderItemStatus.ACCEPTED, actorId, null);
        return new AcceptItemResponse("ACCEPTED", null, orderService.getOrderItemById(itemId));
    }

    @Override
    @Transactional
    public OrderItemResponse rejectOrderItem(long itemId, long actorId, String reason) {
        String cleanReason = OrderServiceImpl.requireReason(reason, "Từ chối món bắt buộc có lý do");
        statusChanger.changeItemStatus(findItemForUpdate(itemId), EnumSet.of(OrderItemStatus.PENDING), OrderItemStatus.REJECTED,
                actorId, cleanReason);
        return orderService.getOrderItemById(itemId);
    }

    @Override
    @Transactional
    public OrderItemResponse startCookingOrderItem(long itemId, long actorId) {
        OrderItem item = findItemForUpdate(itemId);
        if (item.getStatus() != OrderItemStatus.ACCEPTED) {
            throw ApiException.invalidState("Món " + itemId + " đang " + item.getStatus() + ", chưa được nhận để nấu");
        }
        if (item.isHasStockException()) {
            throw ApiException.invalidState("Món " + itemId + " đang giữ lô nguyên liệu bị hỏng: cấp lại nguyên liệu hoặc hủy món");
        }
        kitchenStockAllocator.consumeReservationsForCooking(itemId);
        statusChanger.changeItemStatus(item, EnumSet.of(OrderItemStatus.ACCEPTED), OrderItemStatus.COOKING, actorId, null);
        return orderService.getOrderItemById(itemId);
    }

    @Override
    @Transactional
    public OrderItemResponse finishCookingOrderItem(long itemId, long actorId) {
        statusChanger.changeItemStatus(findItemForUpdate(itemId), EnumSet.of(OrderItemStatus.COOKING), OrderItemStatus.READY, actorId, null);
        return orderService.getOrderItemById(itemId);
    }

    @Override
    @Transactional
    public OrderItemResponse reallocateOrderItemIngredients(long itemId, long actorId) {
        OrderItem item = findItemForUpdate(itemId);
        if (item.getStatus() != OrderItemStatus.ACCEPTED) {
            throw ApiException.invalidState("Món " + itemId + " đang " + item.getStatus() + ", không phải đã nhận");
        }
        kitchenStockAllocator.reallocateUnusableReservations(itemId, item.getDailyMenuItemId(), item.getQuantity());
        item.clearStockException();
        orderItemRepository.flush();
        return orderService.getOrderItemById(itemId);
    }

    /**
     * Lý do món không thể làm: thực đơn hết bán, món tạm dừng, hoặc vượt hạn mức suất. null = đạt.
     * Khóa dòng món trong thực đơn để hai món cùng loại được nhận cùng lúc thì kiểm suất lần lượt.
     */
    private String findReasonDishCannotBeServed(OrderItem item) {
        OrderableDish dish = dailyMenuLookup.findOrderableDishForUpdate(item.getDailyMenuItemId()).orElseThrow();
        if (!dish.onSaleToday()) {
            return "Thực đơn ngày không còn mở bán";
        }
        if (dish.paused()) {
            return "Món đang tạm dừng: " + dish.pauseReason();
        }
        if (dish.quotaTotal() == null) {
            return null;
        }
        long used = quotaUsageCounter.countUsedPortions(item.getDailyMenuItemId());
        if (used + item.getQuantity() <= dish.quotaTotal()) {
            return null;
        }
        return dish.quotaTotal() == 0
                ? "Món không còn suất được bán (hạn mức 0)"
                : "Vượt hạn mức: đã nhận " + used + "/" + dish.quotaTotal() + " suất, cần thêm " + item.getQuantity();
    }

    private OrderItem findItemForUpdate(long itemId) {
        return orderItemRepository.findByIdForUpdate(itemId).orElseThrow(() -> ApiException.notFound("Không tìm thấy món " + itemId));
    }
}
