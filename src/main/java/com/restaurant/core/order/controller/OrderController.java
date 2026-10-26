package com.restaurant.core.order.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.restaurant.core.common.response.ApiResponse;
import com.restaurant.core.order.dto.OrderResponse;
import com.restaurant.core.order.dto.SubmitBatchRequest;
import com.restaurant.core.order.dto.SubmitBatchResponse;
import com.restaurant.core.order.service.OrderService;
import com.restaurant.core.security.CurrentUser;

/** Đơn hàng (tạo cùng phiên bởi rc_open_session) và lượt gọi món. Owner: Phạm Duy An. */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final CurrentUser currentUser;

    public OrderController(OrderService orderService, CurrentUser currentUser) {
        this.orderService = orderService;
        this.currentUser = currentUser;
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasAnyRole('MANAGER', 'WAITER', 'CASHIER', 'KITCHEN')")
    public ApiResponse<OrderResponse> get(@PathVariable @Positive long orderId) {
        return ApiResponse.ok(orderService.getOrder(orderId));
    }

    /** Gửi lượt gọi món (lần đầu và gọi thêm). Idempotent theo requestKey. */
    @PostMapping("/{orderId}/batches")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('WAITER', 'MANAGER')")
    public ApiResponse<SubmitBatchResponse> submitBatch(@PathVariable @Positive long orderId,
                                                        @Valid @RequestBody SubmitBatchRequest request) {
        return ApiResponse.ok(orderService.submitBatch(orderId, currentUser.employeeId(), request));
    }
}
