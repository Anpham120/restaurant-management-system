package vn.khoibep.rms.controller;

import java.util.List;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import vn.khoibep.rms.common.exception.ApiException;
import vn.khoibep.rms.common.security.CurrentUser;
import vn.khoibep.rms.dto.OrderDtos.AddItemsRequest;
import vn.khoibep.rms.dto.OrderDtos.CancelRequest;
import vn.khoibep.rms.dto.OrderDtos.CreateOrderRequest;
import vn.khoibep.rms.dto.OrderDtos.KitchenItemDto;
import vn.khoibep.rms.dto.OrderDtos.MoveTablesRequest;
import vn.khoibep.rms.dto.OrderDtos.OrderDto;
import vn.khoibep.rms.dto.OrderDtos.OrderItemDto;
import vn.khoibep.rms.dto.OrderDtos.StatusRequest;
import vn.khoibep.rms.dto.PaymentDtos.PaymentDto;
import vn.khoibep.rms.enums.OrderStatus;
import vn.khoibep.rms.service.OrderItemService;
import vn.khoibep.rms.service.OrderService;
import vn.khoibep.rms.service.PaymentService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderItemService orderItemService;
    private final PaymentService paymentService;
    private final CurrentUser currentUser;

    /** P3-07: only the open orders are listed; a closed order is read by its id, from a report or a customer. */
    @GetMapping("/orders")
    @PreAuthorize("hasAnyRole('WAITER', 'CASHIER')")
    public List<OrderDto> list(@RequestParam(defaultValue = "OPEN") OrderStatus status) {
        if (status != OrderStatus.OPEN) {
            throw ApiException.badRequest("Chỉ xem được danh sách đơn đang mở");
        }
        return orderService.openOrders();
    }

    @GetMapping("/orders/{id}")
    @PreAuthorize("hasAnyRole('WAITER', 'CASHIER')")
    public OrderDto get(@PathVariable Long id) {
        return orderService.get(id);
    }

    @PostMapping("/orders")
    @PreAuthorize("hasRole('WAITER')")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDto create(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.create(request, currentUser.id());
    }

    @PostMapping("/orders/{id}/items")
    @PreAuthorize("hasRole('WAITER')")
    public OrderDto addItems(@PathVariable Long id, @Valid @RequestBody AddItemsRequest request) {
        return orderService.addStaffItems(id, request, currentUser.id());
    }

    /** FR-04.5, FR-04.6: put tables together or move the order; the bill stays (BR-36). */
    @PostMapping("/orders/{id}/tables")
    @PreAuthorize("hasRole('WAITER')")
    public OrderDto moveTables(@PathVariable Long id, @Valid @RequestBody MoveTablesRequest request) {
        return orderService.moveTables(id, request.tableIds());
    }

    @PostMapping("/orders/{id}/confirm-pending")
    @PreAuthorize("hasRole('WAITER')")
    public OrderDto confirmPending(@PathVariable Long id) {
        return orderService.confirmPending(id, currentUser.id());
    }

    @PostMapping("/orders/{id}/cancel")
    @PreAuthorize("hasRole('WAITER')")
    public OrderDto cancel(@PathVariable Long id) {
        return orderService.cancel(id);
    }

    /** FR-21.3, BR-47: the shipper picks up an app order; the app has the money. */
    @PostMapping("/orders/{id}/handover")
    @PreAuthorize("hasRole('WAITER')")
    public PaymentDto handOver(@PathVariable Long id) {
        return paymentService.handOver(id, currentUser.id());
    }

    /** Finer checks per transition are in OrderItemService (BR-07). */
    @PatchMapping("/order-items/{id}/status")
    @PreAuthorize("hasAnyRole('CHEF', 'WAITER')")
    public OrderItemDto changeStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return orderItemService.changeStatus(id, request.status());
    }

    /** Finer checks per status are in OrderItemService (BR-08). */
    @PostMapping("/order-items/{id}/cancel")
    @PreAuthorize("hasRole('WAITER')")
    public OrderItemDto cancelItem(@PathVariable Long id,
                                   @Valid @RequestBody(required = false) CancelRequest request) {
        return orderItemService.cancel(id, request == null ? null : request.reason());
    }

    @GetMapping("/kitchen/items")
    @PreAuthorize("hasRole('CHEF')")
    public List<KitchenItemDto> kitchenItems() {
        return orderItemService.kitchenItems();
    }
}
