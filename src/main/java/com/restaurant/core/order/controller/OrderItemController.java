package com.restaurant.core.order.controller;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.restaurant.core.common.response.ApiResponse;
import com.restaurant.core.order.dto.AcceptItemResponse;
import com.restaurant.core.order.dto.ItemEventResponse;
import com.restaurant.core.order.dto.OrderItemResponse;
import com.restaurant.core.order.dto.ReasonRequest;
import com.restaurant.core.order.service.KitchenService;
import com.restaurant.core.order.service.OrderService;
import com.restaurant.core.security.CurrentUser;

/**
 * Chuyển trạng thái một dòng món — mỗi endpoint là một hàm rc_* (state machine của DB, docs/01 §7.3).
 * Vai trò theo ma trận docs/07 §10. Owner: Phạm Duy An.
 */
@RestController
@RequestMapping("/api/order-items")
public class OrderItemController {

    private final OrderService orderService;
    private final KitchenService kitchenService;
    private final CurrentUser currentUser;

    public OrderItemController(OrderService orderService, KitchenService kitchenService, CurrentUser currentUser) {
        this.orderService = orderService;
        this.kitchenService = kitchenService;
        this.currentUser = currentUser;
    }

    @GetMapping("/{itemId}/events")
    @PreAuthorize("hasAnyRole('MANAGER', 'WAITER', 'CASHIER', 'KITCHEN')")
    public ApiResponse<List<ItemEventResponse>> events(@PathVariable @Positive long itemId) {
        return ApiResponse.ok(orderService.itemEvents(itemId));
    }

    @PostMapping("/{itemId}/accept")
    @PreAuthorize("hasAnyRole('KITCHEN', 'MANAGER')")
    public ApiResponse<AcceptItemResponse> accept(@PathVariable @Positive long itemId) {
        return ApiResponse.ok(kitchenService.accept(itemId, currentUser.employeeId()));
    }

    @PostMapping("/{itemId}/reject")
    @PreAuthorize("hasAnyRole('KITCHEN', 'MANAGER')")
    public ApiResponse<OrderItemResponse> reject(@PathVariable @Positive long itemId, @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(kitchenService.reject(itemId, currentUser.employeeId(), request.reason()));
    }

    @PostMapping("/{itemId}/start-cooking")
    @PreAuthorize("hasAnyRole('KITCHEN', 'MANAGER')")
    public ApiResponse<OrderItemResponse> startCooking(@PathVariable @Positive long itemId) {
        return ApiResponse.ok(kitchenService.startCooking(itemId, currentUser.employeeId()));
    }

    @PostMapping("/{itemId}/finish")
    @PreAuthorize("hasAnyRole('KITCHEN', 'MANAGER')")
    public ApiResponse<OrderItemResponse> finish(@PathVariable @Positive long itemId) {
        return ApiResponse.ok(kitchenService.finish(itemId, currentUser.employeeId()));
    }

    @PostMapping("/{itemId}/reallocate")
    @PreAuthorize("hasAnyRole('KITCHEN', 'MANAGER')")
    public ApiResponse<OrderItemResponse> reallocate(@PathVariable @Positive long itemId) {
        return ApiResponse.ok(kitchenService.reallocate(itemId, currentUser.employeeId()));
    }

    @PostMapping("/{itemId}/serve")
    @PreAuthorize("hasAnyRole('WAITER', 'MANAGER')")
    public ApiResponse<OrderItemResponse> serve(@PathVariable @Positive long itemId) {
        return ApiResponse.ok(orderService.serve(itemId, currentUser.employeeId()));
    }

    /** Hủy trước khi nấu (PENDING/ACCEPTED). Từ COOKING phải dùng cancel-exception. */
    @PostMapping("/{itemId}/cancel")
    @PreAuthorize("hasAnyRole('WAITER', 'KITCHEN', 'MANAGER')")
    public ApiResponse<OrderItemResponse> cancel(@PathVariable @Positive long itemId, @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(orderService.cancel(itemId, currentUser.employeeId(), request.reason()));
    }

    @PostMapping("/{itemId}/cancel-exception")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<OrderItemResponse> cancelException(@PathVariable @Positive long itemId, @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(orderService.cancelException(itemId, currentUser.employeeId(), request.reason()));
    }
}
