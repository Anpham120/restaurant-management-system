package com.restaurant.core.finance.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.restaurant.core.common.response.ApiResponse;
import com.restaurant.core.finance.dto.InvoiceResponse;
import com.restaurant.core.finance.dto.ReasonRequest;
import com.restaurant.core.finance.service.InvoiceService;
import com.restaurant.core.security.CurrentUser;

/** Hóa đơn của một đơn hàng. Owner: Phạm Duy An (vòng đời đơn hàng), không thuộc phần tài chính của Bùi Đào Đức Anh. */
@RestController
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final CurrentUser currentUser;

    public InvoiceController(InvoiceService invoiceService, CurrentUser currentUser) {
        this.invoiceService = invoiceService;
        this.currentUser = currentUser;
    }

    /** Chốt hóa đơn: mọi món đã kết thúc, ≥ 1 món SERVED, tổng > 0 → PAYMENT_PENDING (gọi lại khi DRAFT sau mở lại). */
    @PostMapping("/api/orders/{orderId}/invoice")
    @PreAuthorize("hasAnyRole('CASHIER', 'MANAGER')")
    public ApiResponse<InvoiceResponse> checkout(@PathVariable @Positive long orderId) {
        return ApiResponse.ok(invoiceService.checkout(orderId, currentUser.employeeId()));
    }

    @GetMapping("/api/orders/{orderId}/invoice")
    @PreAuthorize("hasAnyRole('CASHIER', 'MANAGER', 'WAITER')")
    public ApiResponse<InvoiceResponse> getByOrder(@PathVariable @Positive long orderId) {
        return ApiResponse.ok(invoiceService.getByOrder(orderId));
    }

    @GetMapping("/api/invoices/{invoiceId}")
    @PreAuthorize("hasAnyRole('CASHIER', 'MANAGER', 'WAITER')")
    public ApiResponse<InvoiceResponse> get(@PathVariable @Positive long invoiceId) {
        return ApiResponse.ok(invoiceService.get(invoiceId));
    }

    @PostMapping("/api/invoices/{invoiceId}/reopen")
    @PreAuthorize("hasAnyRole('CASHIER', 'MANAGER')")
    public ApiResponse<InvoiceResponse> reopen(@PathVariable @Positive long invoiceId, @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(invoiceService.reopen(invoiceId, currentUser.employeeId(), request.reason()));
    }

    /** Miễn thu (WAIVED) — chỉ Manager; cũng là cách đóng hóa đơn 0 đồng. */
    @PostMapping("/api/orders/{orderId}/waive")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<InvoiceResponse> waive(@PathVariable @Positive long orderId, @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(invoiceService.waive(orderId, currentUser.employeeId(), request.reason()));
    }
}
