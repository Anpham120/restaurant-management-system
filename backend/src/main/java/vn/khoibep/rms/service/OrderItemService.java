package vn.khoibep.rms.service;

import java.util.EnumSet;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.khoibep.rms.common.exception.ApiException;
import vn.khoibep.rms.common.realtime.RealtimeEvent.Alert;
import vn.khoibep.rms.common.realtime.RealtimeEvents;
import vn.khoibep.rms.common.security.CurrentUser;
import vn.khoibep.rms.dto.OrderDtos.KitchenItemDto;
import vn.khoibep.rms.dto.OrderDtos.OrderItemDto;
import vn.khoibep.rms.enums.AuditAction;
import vn.khoibep.rms.enums.ItemStatus;
import vn.khoibep.rms.enums.Role;
import vn.khoibep.rms.model.Adjustment;
import vn.khoibep.rms.model.Order;
import vn.khoibep.rms.model.OrderItem;
import vn.khoibep.rms.repository.OrderItemRepository;
import vn.khoibep.rms.repository.OrderRepository;

@Service
@RequiredArgsConstructor
public class OrderItemService {

    private final OrderItemRepository items;
    private final OrderRepository orders;
    private final PaymentService payments;
    private final StockUsageService stockUsage;
    private final AuditService audit;
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
        // BR-43: part of the bill is already taken, so the bill can only grow.
        if (item.getStatus().isBillable() && order.paidAmount() > 0) {
            throw ApiException.conflict("Đơn đã thu một phần, không huỷ món được nữa");
        }
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
        ItemStatus before = item.getStatus();
        boolean wasBillable = before.isBillable();
        item.cancel(why);
        // BR-34: who cancelled which dish, and how far it had gone.
        audit.record(AuditAction.ITEM_CANCELLED, order, item.getItemName() + " x" + item.getQuantity(), before.name(),
                ItemStatus.CANCELLED.name(), item.getUnitPrice() * item.getQuantity(), why);
        // BR-35: a dish given free and then cancelled takes nothing off the bill any more.
        order.getAdjustments().stream().filter(a -> a.isOpen() && a.gives(item)).forEach(Adjustment::cancel);
        // BR-38: a dish the kitchen had not started gives its ingredients back; one being cooked has used them.
        if (before == ItemStatus.WAITING) {
            stockUsage.giveBack(item.getId(), "Huỷ món, hoàn kho · " + OrderService.dishNote(order, item),
                    currentUser.id());
        }
        if (wasBillable) {
            payments.cancelPendingTransfers(orderId);
        }
        publish(order, null);
        return OrderItemDto.from(item);
    }

    private void publish(Order order, Alert alert) {
        realtime.orderChanged(order.getId(), order.tableId(), order.guestTokens(), alert);
    }
}
