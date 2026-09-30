package vn.bnn.rms.order;

import java.util.EnumSet;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.bnn.rms.common.ApiException;
import vn.bnn.rms.common.CurrentUser;
import vn.bnn.rms.common.RealtimeEvent.Alert;
import vn.bnn.rms.common.RealtimeEvents;
import vn.bnn.rms.employee.Role;
import vn.bnn.rms.order.OrderDtos.KitchenItemDto;
import vn.bnn.rms.order.OrderDtos.OrderItemDto;
import vn.bnn.rms.payment.PaymentService;

@Service
@RequiredArgsConstructor
public class OrderItemService {

    private final OrderItemRepository items;
    private final OrderRepository orders;
    private final PaymentService payments;
    private final CurrentUser currentUser;
    private final RealtimeEvents realtime;

    /** FR-07.1: guest dishes waiting for confirmation are not shown to the kitchen (BR-10). */
    @Transactional(readOnly = true)
    public List<KitchenItemDto> kitchenItems() {
        return items.findKitchenItems(EnumSet.of(ItemStatus.WAITING, ItemStatus.COOKING, ItemStatus.READY))
                .stream().map(KitchenItemDto::from).toList();
    }

    /** BR-07: the kitchen moves WAITING → COOKING → READY; a waiter moves READY → SERVED. */
    @Transactional
    public OrderItemDto changeStatus(Long itemId, ItemStatus next) {
        switch (next) {
            case COOKING, READY -> currentUser.require(Role.CHEF, "Chỉ bếp được đổi trạng thái chế biến");
            case SERVED -> currentUser.require(Role.WAITER, "Chỉ phục vụ được xác nhận đã ra món");
            default -> throw ApiException.badRequest("Dùng API xác nhận hoặc huỷ cho trạng thái này");
        }
        OrderItem item = items.findById(itemId).orElseThrow(() -> ApiException.notFound("Không tìm thấy món"));
        item.moveTo(next);
        publish(item.getOrder(), next == ItemStatus.READY ? Alert.DISH_READY : null);
        return OrderItemDto.from(item);
    }

    /** BR-08, BR-10: who may cancel depends on how far the dish has gone. */
    @Transactional
    public OrderItemDto cancel(Long itemId, String reason) {
        Long orderId = items.findOrderIdById(itemId).orElseThrow(() -> ApiException.notFound("Không tìm thấy món"));
        Order order = orders.findByIdForUpdate(orderId).orElseThrow();
        if (!order.isOpen()) {
            throw ApiException.conflict("Đơn đã đóng, không huỷ món được");
        }
        OrderItem item = items.findById(itemId).orElseThrow();
        String why = reason == null || reason.isBlank() ? null : reason.trim();
        switch (item.getStatus()) {
            case PENDING -> {
                currentUser.require(Role.WAITER, "Bạn không có quyền từ chối món");
                if (why == null) {
                    throw ApiException.badRequest("Cần nhập lý do từ chối để khách biết");
                }
            }
            case WAITING -> currentUser.require(Role.WAITER, "Bạn không có quyền huỷ món");
            case COOKING, READY -> {
                currentUser.require(Role.MANAGER, "Món đang làm hoặc đã xong chỉ quản lý được huỷ");
                if (why == null) {
                    throw ApiException.badRequest("Cần nhập lý do huỷ");
                }
            }
            default -> throw ApiException.conflict("Món đã ra hoặc đã huỷ, không huỷ được");
        }
        boolean wasBillable = item.getStatus().isBillable();
        item.cancel(why);
        if (wasBillable) {
            payments.cancelPendingTransfers(orderId);
        }
        publish(order, null);
        return OrderItemDto.from(item);
    }

    private void publish(Order order, Alert alert) {
        realtime.orderChanged(order.getId(), order.tableId(), order.guestToken(), alert);
    }
}
