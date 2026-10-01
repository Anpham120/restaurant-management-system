package vn.khoibep.rms.order.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.khoibep.rms.common.exception.ApiException;
import vn.khoibep.rms.common.realtime.RealtimeEvent.Alert;
import vn.khoibep.rms.common.realtime.RealtimeEvents;
import vn.khoibep.rms.menu.entity.MenuItem;
import vn.khoibep.rms.menu.repository.MenuItemRepository;
import vn.khoibep.rms.order.dto.OrderDtos.AddItemsRequest;
import vn.khoibep.rms.order.dto.OrderDtos.CreateOrderRequest;
import vn.khoibep.rms.order.dto.OrderDtos.ItemLine;
import vn.khoibep.rms.order.dto.OrderDtos.OrderDto;
import vn.khoibep.rms.order.entity.Adjustment;
import vn.khoibep.rms.order.entity.Order;
import vn.khoibep.rms.order.entity.OrderItem;
import vn.khoibep.rms.order.enums.ItemSource;
import vn.khoibep.rms.order.enums.ItemStatus;
import vn.khoibep.rms.order.enums.OrderStatus;
import vn.khoibep.rms.order.enums.OrderType;
import vn.khoibep.rms.order.repository.OrderRepository;
import vn.khoibep.rms.payment.service.PaymentService;
import vn.khoibep.rms.table.entity.DiningTable;
import vn.khoibep.rms.table.repository.DiningTableRepository;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orders;
    private final DiningTableRepository tables;
    private final MenuItemRepository menuItems;
    private final PaymentService payments;
    private final RealtimeEvents realtime;

    @Transactional(readOnly = true)
    public List<OrderDto> list(OrderStatus status) {
        return orders.findWithItemsByStatus(status).stream().map(OrderDto::from).toList();
    }

    @Transactional(readOnly = true)
    public OrderDto get(Long id) {
        return OrderDto.from(orders.findWithItemsById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn")));
    }

    /** FR-05.1, BR-04: one open order per table. */
    @Transactional
    public OrderDto create(CreateOrderRequest request, Long employeeId) {
        Order order = new Order();
        order.setType(request.type());
        order.setGuestCount(request.guestCount());
        order.setNote(request.note());
        order.setCreatedBy(employeeId);
        if (request.type() == OrderType.DINE_IN) {
            if (request.tableId() == null) {
                throw ApiException.badRequest("Đơn tại bàn phải chọn bàn");
            }
            DiningTable table = tables.findById(request.tableId())
                    .orElseThrow(() -> ApiException.notFound("Không tìm thấy bàn"));
            if (orders.findByTableIdAndStatus(table.getId(), OrderStatus.OPEN).isPresent()) {
                throw ApiException.conflict("Bàn đã có đơn đang mở");
            }
            order.setTable(table);
        }
        // Flush now so a concurrent open of the same table fails on ux_orders_open_table here.
        orders.saveAndFlush(order);
        publish(order);
        return OrderDto.from(order);
    }

    /** FR-05.2, FR-05.3: staff dishes go straight to the kitchen. */
    @Transactional
    public OrderDto addStaffItems(Long orderId, AddItemsRequest request) {
        Order order = lockOpen(orderId);
        buildItems(request.items(), ItemSource.STAFF).forEach(order::addItem);
        payments.cancelPendingTransfers(orderId);
        orders.flush();
        publish(order, Alert.NEW_DISHES);
        return OrderDto.from(order);
    }

    /** FR-06.3: confirmed guest dishes enter the kitchen. */
    @Transactional
    public OrderDto confirmPending(Long orderId) {
        Order order = lockOpen(orderId);
        List<OrderItem> pending = order.itemsWith(ItemStatus.PENDING);
        if (pending.isEmpty()) {
            throw ApiException.conflict("Không có món chờ xác nhận");
        }
        pending.forEach(item -> item.moveTo(ItemStatus.WAITING));
        payments.cancelPendingTransfers(orderId);
        publish(order, Alert.NEW_DISHES);
        return OrderDto.from(order);
    }

    /** FR-05.6: only when every dish is cancelled; the table becomes free. */
    @Transactional
    public OrderDto cancel(Long orderId) {
        Order order = lockOpen(orderId);
        if (order.getItems().stream().anyMatch(i -> i.getStatus() != ItemStatus.CANCELLED)) {
            throw ApiException.conflict("Chỉ huỷ được đơn khi mọi món đã huỷ");
        }
        payments.cancelPendingTransfers(orderId);
        // BR-35: nothing is left for a manager to decide on a cancelled order.
        order.getAdjustments().stream().filter(Adjustment::isOpen).forEach(Adjustment::cancel);
        order.close(OrderStatus.CANCELLED);
        publish(order);
        return OrderDto.from(order);
    }

    /** BR-05 (price snapshot) and BR-06 (dish must be on sale). */
    List<OrderItem> buildItems(List<ItemLine> lines, ItemSource source) {
        Map<Long, MenuItem> byId = menuItems.findAllById(lines.stream().map(ItemLine::menuItemId).toList())
                .stream().collect(Collectors.toMap(MenuItem::getId, Function.identity()));
        List<OrderItem> result = new ArrayList<>();
        for (ItemLine line : lines) {
            MenuItem menuItem = byId.get(line.menuItemId());
            if (menuItem == null) {
                throw ApiException.notFound("Không tìm thấy món #" + line.menuItemId());
            }
            if (!menuItem.isAvailable()) {
                throw ApiException.conflict("Món '" + menuItem.getName() + "' đã hết");
            }
            String note = line.note() == null || line.note().isBlank() ? null : line.note().trim();
            result.add(OrderItem.create(menuItem, line.quantity(), note, source));
        }
        return result;
    }

    private Order lockOpen(Long orderId) {
        Order order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn"));
        if (!order.isOpen()) {
            throw ApiException.conflict("Đơn đã đóng");
        }
        return order;
    }

    private void publish(Order order) {
        publish(order, null);
    }

    private void publish(Order order, Alert alert) {
        realtime.orderChanged(order.getId(), order.tableId(), order.guestToken(), alert);
    }
}
