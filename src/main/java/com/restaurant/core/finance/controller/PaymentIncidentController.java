package com.restaurant.core.finance.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.restaurant.core.common.response.ApiResponse;
import com.restaurant.core.common.response.PageResponse;
import com.restaurant.core.finance.dto.request.ManualTransferRequest;
import com.restaurant.core.finance.dto.request.ResolveIncidentRequest;
import com.restaurant.core.finance.dto.response.ManualTransferResponse;
import com.restaurant.core.finance.dto.response.PaymentIncidentResponse;
import com.restaurant.core.finance.service.PaymentIncidentService;
import com.restaurant.core.security.CurrentUser;

/** Đối soát sự cố chuyển khoản QR. Chỉ Quản lý. Owner: Phạm Duy An. */
@RestController
@RequestMapping("/api")
public class PaymentIncidentController {

    private final PaymentIncidentService incidentService;
    private final CurrentUser currentUser;

    public PaymentIncidentController(PaymentIncidentService incidentService, CurrentUser currentUser) {
        this.incidentService = incidentService;
        this.currentUser = currentUser;
    }

    /** status = OPEN (mặc định) | RESOLVED | ALL. */
    @GetMapping("/payment-incidents")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<PageResponse<PaymentIncidentResponse>> getAllPaymentIncidents(
            @RequestParam(defaultValue = "OPEN") @Pattern(regexp = "OPEN|RESOLVED|ALL") String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(incidentService.getAllPaymentIncidents("ALL".equals(status) ? null : status, page, size));
    }

    /** Kèm payload gốc của ngân hàng để đối chiếu. */
    @GetMapping("/payment-incidents/{incidentId}")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<PaymentIncidentResponse> getPaymentIncidentById(@PathVariable @Positive long incidentId) {
        return ApiResponse.ok(incidentService.getPaymentIncidentById(incidentId));
    }

    @PostMapping("/payment-incidents/{incidentId}/resolve")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<PaymentIncidentResponse> resolvePaymentIncident(@PathVariable @Positive long incidentId,
                                                                      @Valid @RequestBody ResolveIncidentRequest request) {
        return ApiResponse.ok(incidentService.resolvePaymentIncident(incidentId, currentUser.employeeId(), request.note()));
    }

    /** Webhook bị mất: ghi nhận tay một chuyển khoản đã thấy trên sao kê / trang giao dịch SePay. */
    @PostMapping("/payment-requests/{requestId}/manual-transfer")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<ManualTransferResponse> recordManualTransfer(@PathVariable @Positive long requestId,
                                                                   @Valid @RequestBody ManualTransferRequest request) {
        return ApiResponse.ok(incidentService.recordManualTransfer(requestId, currentUser.employeeId(), request));
    }
}
