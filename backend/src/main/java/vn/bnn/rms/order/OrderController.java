package vn.bnn.rms.order;

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

import vn.bnn.rms.common.CurrentUser;
import vn.bnn.rms.order.OrderDtos.AddItemsRequest;
import vn.bnn.rms.order.OrderDtos.CancelRequest;
import vn.bnn.rms.order.OrderDtos.CreateOrderRequest;
import vn.bnn.rms.order.OrderDtos.KitchenItemDto;
import vn.bnn.rms.order.OrderDtos.OrderDto;
import vn.bnn.rms.order.OrderDtos.OrderItemDto;
import vn.bnn.rms.order.OrderDtos.StatusRequest;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderItemService orderItemService;
    private final CurrentUser currentUser;

    @GetMapping("/orders")
    @PreAuthorize("hasAnyRole('WAITER', 'CASHIER')")
    public List<OrderDto> list(@RequestParam(defaultValue = "OPEN") OrderStatus status) {
        return orderService.list(status);
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
        return orderService.addStaffItems(id, request);
    }

    @PostMapping("/orders/{id}/confirm-pending")
    @PreAuthorize("hasRole('WAITER')")
    public OrderDto confirmPending(@PathVariable Long id) {
        return orderService.confirmPending(id);
    }

    @PostMapping("/orders/{id}/cancel")
    @PreAuthorize("hasRole('WAITER')")
    public OrderDto cancel(@PathVariable Long id) {
        return orderService.cancel(id);
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
