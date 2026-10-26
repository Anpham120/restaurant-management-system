package com.restaurant.core.finance.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.restaurant.core.common.response.ApiResponse;
import com.restaurant.core.finance.dto.InvoiceResponse;
import com.restaurant.core.finance.dto.PaymentRequest;
import com.restaurant.core.finance.dto.ReasonRequest;
import com.restaurant.core.finance.dto.PaymentResponse;
import com.restaurant.core.finance.service.PaymentService;
import com.restaurant.core.security.CurrentUser;

/** Thanh toán CASH/QR tại quầy. Owner: Phạm Duy An. */
@RestController
@RequestMapping("/api")
public class PaymentController {

    private final PaymentService paymentService;
    private final CurrentUser currentUser;

    public PaymentController(PaymentService paymentService, CurrentUser currentUser) {
        this.paymentService = paymentService;
        this.currentUser = currentUser;
    }

    /** Thu tiền một hóa đơn đã chốt (PAYMENT_PENDING). Vai trò khớp rc_pay_cash/rc_start_qr: CASHIER, MANAGER. */
    @PostMapping("/invoices/{invoiceId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('CASHIER', 'MANAGER')")
    public ApiResponse<PaymentResponse> pay(@PathVariable @Positive long invoiceId, @Valid @RequestBody PaymentRequest request) {
        return ApiResponse.ok(paymentService.start(invoiceId, currentUser.employeeId(), request));
    }

    /** Hủy yêu cầu thanh toán QR đang chờ (khách đổi sang tiền mặt…). Webhook đến sau sẽ được lưu AFTER_CANCEL. */
    @PostMapping("/payment-requests/{requestId}/cancel")
    @PreAuthorize("hasAnyRole('CASHIER', 'MANAGER')")
    public ApiResponse<InvoiceResponse> cancelRequest(@PathVariable @Positive long requestId, @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(paymentService.cancelRequest(requestId, currentUser.employeeId(), request.reason()));
    }
}
