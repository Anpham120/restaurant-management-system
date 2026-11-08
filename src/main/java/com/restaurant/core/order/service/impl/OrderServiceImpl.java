package com.restaurant.core.order.service.impl;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.restaurant.core.common.exception.ApiException;
import com.restaurant.core.common.time.BusinessDateProvider;
import com.restaurant.core.inventory.KitchenStockAllocator;
import com.restaurant.core.menu.DailyMenuLookup.OrderableDish;
import com.restaurant.core.menu.DailyMenuLookup;
import com.restaurant.core.menu.QuotaUsageCounter;
import com.restaurant.core.order.OrderInvoiceAccess;
import com.restaurant.core.order.dto.response.ItemEventResponse;
import com.restaurant.core.order.dto.response.OrderItemResponse;
import com.restaurant.core.order.dto.response.OrderResponse;
import com.restaurant.core.order.dto.request.SubmitBatchRequest;
import com.restaurant.core.order.dto.response.SubmitBatchResponse.QuotaWarning;
import com.restaurant.core.order.dto.response.SubmitBatchResponse;
import com.restaurant.core.order.entity.CustomerOrder;
import com.restaurant.core.order.entity.OrderBatch;
import com.restaurant.core.order.entity.OrderItem;
import com.restaurant.core.order.entity.OrderItemStatus;
import com.restaurant.core.order.repository.CustomerOrderRepository;
import com.restaurant.core.order.repository.OrderBatchRepository;
import com.restaurant.core.order.repository.OrderItemRepository;
import com.restaurant.core.order.repository.OrderQueryRepository;
import com.restaurant.core.order.repository.TableQueryRepository;
import com.restaurant.core.order.service.OrderService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {

    /** Hóa đơn ở các trạng thái này: không gọi thêm món được (phải mở lại hóa đơn trước). */
    private static final Set<String> INVOICE_LOCKS_ORDERING = Set.of("PAYMENT_PENDING", "PAID", "UNCOLLECTED");
    private static final Set<OrderItemStatus> COOKED = EnumSet.of(OrderItemStatus.COOKING, OrderItemStatus.READY, OrderItemStatus.SERVED);
    private static final Set<OrderItemStatus> NOT_COOKED = EnumSet.of(OrderItemStatus.PENDING, OrderItemStatus.ACCEPTED);

    private final CustomerOrderRepository customerOrderRepository;
    private final OrderBatchRepository orderBatchRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderQueryRepository orderQueryRepository;
    private final TableQueryRepository tableQueryRepository;
    private final OrderItemStatusChanger statusChanger;
    private final DailyMenuLookup dailyMenuLookup;
    private final QuotaUsageCounter quotaUsageCounter;
    private final KitchenStockAllocator kitchenStockAllocator;
    private final OrderInvoiceAccess invoiceAccess;
    private final BusinessDateProvider clock;

    // ---------- đọc

    @Override
    public OrderResponse getOrderById(long orderId) {
        OrderQueryRepository.OrderRow order = orderQueryRepository.findOrder(orderId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn " + orderId));
        return new OrderResponse(order.id(), order.status(), tableQueryRepository.findSession(order.tableSessionId()).orElseThrow(),
                orderQueryRepository.findItemsOfOrder(orderId));
    }

    @Override
    public OrderItemResponse getOrderItemById(long itemId) {
        return orderQueryRepository.findItem(itemId).orElseThrow(() -> ApiException.notFound("Không tìm thấy món " + itemId));
    }

    @Override
    public List<ItemEventResponse> getOrderItemHistory(long itemId) {
        getOrderItemById(itemId);
        return orderQueryRepository.findItemEvents(itemId);
    }

    // ---------- gọi món

    @Override
    @Transactional
    public SubmitBatchResponse submitOrderBatch(long orderId, long actorId, SubmitBatchRequest request) {
        String requestKey = request.requestKey().strip();
        var alreadySubmitted = findBatchSubmittedEarlier(requestKey, orderId);
        if (alreadySubmitted != null) {
            return buildBatchResult(alreadySubmitted, true);
        }
        CustomerOrder order = customerOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn " + orderId));
        if (!order.isOpen()) {
            throw ApiException.invalidState("Đơn " + orderId + " không còn mở");
        }
        alreadySubmitted = findBatchSubmittedEarlier(requestKey, orderId);   // một yêu cầu song song cùng khóa vừa ghi xong
        if (alreadySubmitted != null) {
            return buildBatchResult(alreadySubmitted, true);
        }
        invoiceAccess.findInvoiceStatus(orderId).filter(INVOICE_LOCKS_ORDERING::contains).ifPresent(status -> {
            throw ApiException.invalidState("Đơn " + orderId + " đang thanh toán: mở lại hóa đơn trước khi gọi thêm");
        });

        OrderBatch batch = orderBatchRepository.save(new OrderBatch(order, actorId, requestKey, clock.now()));
        for (SubmitBatchRequest.Item line : request.items()) {
            OrderableDish dish = findDishOnSaleToday(line.dailyMenuItemId());
            OrderItem item = orderItemRepository.save(new OrderItem(batch, dish.dailyMenuItemId(), line.quantity(), dish.name(),
                    dish.price(), blankToNull(line.note())));
            statusChanger.recordItemCreated(item, actorId);
        }
        return buildBatchResult(batch, false);
    }

    @Override
    @Transactional
    public OrderItemResponse markItemServed(long itemId, long actorId) {
        statusChanger.changeItemStatus(findItemForUpdate(itemId), EnumSet.of(OrderItemStatus.READY), OrderItemStatus.SERVED, actorId, null);
        return getOrderItemById(itemId);
    }

    @Override
    @Transactional
    public OrderItemResponse cancelItemBeforeCooking(long itemId, long actorId, String reason) {
        String cleanReason = requireReason(reason, "Hủy món bắt buộc có lý do");
        OrderItem item = findItemForUpdate(itemId);
        if (COOKED.contains(item.getStatus())) {
            throw ApiException.invalidState("Món " + itemId + " đã " + item.getStatus() + ": chỉ Quản lý hủy ngoại lệ được");
        }
        if (!NOT_COOKED.contains(item.getStatus())) {
            throw ApiException.invalidState("Món " + itemId + " đang " + item.getStatus() + ", không hủy được");
        }
        kitchenStockAllocator.releaseReservationsOfItem(itemId);
        item.clearStockException();
        statusChanger.changeItemStatus(item, NOT_COOKED, OrderItemStatus.CANCELLED, actorId, cleanReason);
        return getOrderItemById(itemId);
    }

    @Override
    @Transactional
    public OrderItemResponse cancelItemAfterCooking(long itemId, long actorId, String reason) {
        String cleanReason = requireReason(reason, "Hủy ngoại lệ bắt buộc có lý do");
        long orderId = orderItemRepository.findById(itemId).map(OrderItem::getOrderId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy món " + itemId));
        CustomerOrder order = customerOrderRepository.findByIdForUpdate(orderId).orElseThrow();
        if (!order.isOpen()) {
            throw ApiException.invalidState("Đơn " + orderId + " đã " + order.getStatus() + ": không hủy món ngoại lệ");
        }
        String invoiceStatus = invoiceAccess.findInvoiceStatusForUpdate(orderId).orElse(null);
        if ("PAYMENT_PENDING".equals(invoiceStatus)) {
            throw ApiException.invalidState("Hóa đơn đang chờ thanh toán: mở lại hóa đơn trước khi hủy món");
        }
        if (invoiceStatus != null && !"DRAFT".equals(invoiceStatus)) {
            throw ApiException.invalidState("Hóa đơn đã " + invoiceStatus + ": không hủy món ngoại lệ được");
        }
        OrderItem item = findItemForUpdate(itemId);
        if (!COOKED.contains(item.getStatus())) {
            throw ApiException.invalidState("Món " + itemId + " đang " + item.getStatus() + ": hủy ngoại lệ chỉ áp dụng từ lúc đã nấu");
        }
        if (invoiceStatus != null) {
            invoiceAccess.removeItemFromDraftInvoice(orderId, itemId);
        }
        statusChanger.changeItemStatus(item, COOKED, OrderItemStatus.CANCELLED, actorId, cleanReason);
        return getOrderItemById(itemId);
    }

    // ---------- hỗ trợ

    /** Lượt món đã gửi trước đó với cùng requestKey; khóa đã dùng cho đơn khác → lỗi. */
    private OrderBatch findBatchSubmittedEarlier(String requestKey, long orderId) {
        return orderBatchRepository.findByRequestKey(requestKey).map(batch -> {
            if (batch.getOrderId() != orderId) {
                throw ApiException.invalidState("requestKey " + requestKey + " đã dùng cho đơn khác");
            }
            return batch;
        }).orElse(null);
    }

    private OrderableDish findDishOnSaleToday(long dailyMenuItemId) {
        OrderableDish dish = dailyMenuLookup.findDish(dailyMenuItemId)
                .filter(OrderableDish::onSaleToday)
                .orElseThrow(() -> ApiException.invalidState("Món " + dailyMenuItemId + " không có trong thực đơn đang bán hôm nay"));
        if (!dish.menuItemActive()) {
            throw ApiException.invalidState("Món " + dish.name() + " đã ngừng kinh doanh");
        }
        if (dish.paused()) {
            throw ApiException.invalidState("Món " + dish.name() + " đang tạm dừng");
        }
        return dish;
    }

    /** Kết quả lượt món + cảnh báo các món đang chờ bếp mà số lượng vượt số suất còn lại. */
    private SubmitBatchResponse buildBatchResult(OrderBatch batch, boolean replayed) {
        List<OrderItem> items = orderItemRepository.findAllOfBatch(batch.getId());
        List<Long> dishIds = items.stream().map(OrderItem::getDailyMenuItemId).distinct().toList();
        Map<Long, Long> usedByDish = quotaUsageCounter.countUsedPortions(dishIds);
        List<QuotaWarning> warnings = new ArrayList<>();
        for (OrderItem item : items) {
            if (item.getStatus() != OrderItemStatus.PENDING) {
                continue;
            }
            Integer quota = dailyMenuLookup.findDish(item.getDailyMenuItemId()).map(OrderableDish::quotaTotal).orElse(null);
            if (quota == null) {
                continue;
            }
            long used = usedByDish.get(item.getDailyMenuItemId());
            long remaining = Math.max(quota - used, 0);
            if (item.getQuantity() > remaining) {
                warnings.add(new QuotaWarning("QUOTA_EXCEEDED", item.getId(), item.getDailyMenuItemId(), item.getItemNameSnapshot(),
                        item.getQuantity(), quota, used, remaining));
            }
        }
        return new SubmitBatchResponse(batch.getId(), replayed, warnings, orderQueryRepository.findItemsOfBatch(batch.getId()));
    }

    private OrderItem findItemForUpdate(long itemId) {
        return orderItemRepository.findByIdForUpdate(itemId).orElseThrow(() -> ApiException.notFound("Không tìm thấy món " + itemId));
    }

    static String requireReason(String reason, String message) {
        if (reason == null || reason.isBlank()) {
            throw ApiException.invalidRequest(message);
        }
        return reason.strip();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
