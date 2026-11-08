package com.restaurant.core.inventory.controller;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.restaurant.core.common.response.ApiResponse;
import com.restaurant.core.inventory.dto.request.KitchenStockRequests.AdjustmentRequest;
import com.restaurant.core.inventory.dto.response.KitchenStockResponses.AdjustmentResponse;
import com.restaurant.core.inventory.dto.response.KitchenStockResponses.MarkUnusableResponse;
import com.restaurant.core.inventory.dto.request.KitchenStockRequests.PreparationRequest;
import com.restaurant.core.inventory.dto.response.KitchenStockResponses.PreparationResponse;
import com.restaurant.core.inventory.dto.request.KitchenStockRequests.ReturnRequest;
import com.restaurant.core.inventory.dto.response.KitchenStockResponses.ReturnResponse;
import com.restaurant.core.inventory.dto.request.StockDocumentRequests.ReasonRequest;
import com.restaurant.core.inventory.service.KitchenStockService;
import com.restaurant.core.security.CurrentUser;

/** Sơ chế (4.4), hoàn trả (4.5), hủy hàng và lô không dùng được (4.6). Owner: Đỗ Tuấn Anh. */
@RestController
@RequestMapping("/api")
public class KitchenStockController {

    private final KitchenStockService service;
    private final CurrentUser currentUser;

    public KitchenStockController(KitchenStockService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping("/kitchen-preparations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('MANAGER', 'KITCHEN')")
    public ApiResponse<PreparationResponse> createPreparation(@Valid @RequestBody PreparationRequest request) {
        return ApiResponse.ok(service.createPreparation(currentUser.employeeId(), request));
    }

    @GetMapping("/kitchen-preparations")
    @PreAuthorize("hasAnyRole('MANAGER', 'KITCHEN')")
    public ApiResponse<List<PreparationResponse.Header>> getPreparations(@RequestParam(required = false) @Pattern(regexp = "DRAFT|CONFIRMED|CANCELLED") String status,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(service.getPreparations(status, page, size));
    }

    @GetMapping("/kitchen-preparations/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'KITCHEN')")
    public ApiResponse<PreparationResponse> getPreparationById(@PathVariable @Positive long id) {
        return ApiResponse.ok(service.getPreparationById(id));
    }

    /** Trừ WIP, tạo lô bếp PREPARED cho từng đầu ra, ghi hao hụt. */
    @PostMapping("/kitchen-preparations/{id}/confirm")
    @PreAuthorize("hasAnyRole('MANAGER', 'KITCHEN')")
    public ApiResponse<PreparationResponse> confirmPreparation(@PathVariable @Positive long id) {
        return ApiResponse.ok(service.confirmPreparation(id, currentUser.employeeId()));
    }

    @PostMapping("/stock-returns")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('MANAGER', 'KITCHEN')")
    public ApiResponse<ReturnResponse> createStockReturn(@Valid @RequestBody ReturnRequest request) {
        return ApiResponse.ok(service.createStockReturn(currentUser.employeeId(), request));
    }

    @GetMapping("/stock-returns")
    @PreAuthorize("hasAnyRole('MANAGER', 'KITCHEN')")
    public ApiResponse<List<ReturnResponse.Header>> getStockReturns(@RequestParam(required = false) @Pattern(regexp = "DRAFT|CONFIRMED|CANCELLED") String status,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(service.getStockReturns(status, page, size));
    }

    @GetMapping("/stock-returns/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'KITCHEN')")
    public ApiResponse<ReturnResponse> getStockReturnById(@PathVariable @Positive long id) {
        return ApiResponse.ok(service.getStockReturnById(id));
    }

    @PostMapping("/stock-returns/{id}/confirm")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<ReturnResponse> confirmStockReturn(@PathVariable @Positive long id) {
        return ApiResponse.ok(service.confirmStockReturn(id, currentUser.employeeId()));
    }

    @PostMapping("/stock-adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('MANAGER', 'KITCHEN')")
    public ApiResponse<AdjustmentResponse> createStockAdjustment(@Valid @RequestBody AdjustmentRequest request) {
        return ApiResponse.ok(service.createStockAdjustment(currentUser.employeeId(), request));
    }

    @GetMapping("/stock-adjustments")
    @PreAuthorize("hasAnyRole('MANAGER', 'KITCHEN')")
    public ApiResponse<List<AdjustmentResponse.Header>> getStockAdjustments(@RequestParam(required = false) @Pattern(regexp = "DRAFT|CONFIRMED|CANCELLED") String status,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(service.getStockAdjustments(status, page, size));
    }

    @GetMapping("/stock-adjustments/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'KITCHEN')")
    public ApiResponse<AdjustmentResponse> getStockAdjustmentById(@PathVariable @Positive long id) {
        return ApiResponse.ok(service.getStockAdjustmentById(id));
    }

    @PostMapping("/stock-adjustments/{id}/confirm")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<AdjustmentResponse> confirmStockAdjustment(@PathVariable @Positive long id) {
        return ApiResponse.ok(service.confirmStockAdjustment(id, currentUser.employeeId()));
    }

    /** kind = RAW (lô kho) | KITCHEN (lô bếp). Trả số món đang giữ lô bị gắn cờ cần xử lý. */
    @PostMapping("/stock-batches/{kind}/{batchId}/mark-unusable")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<MarkUnusableResponse> markBatchUnusable(@PathVariable @Pattern(regexp = "RAW|KITCHEN") String kind,
                                                          @PathVariable @Positive long batchId, @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.markBatchUnusable(kind, batchId, currentUser.employeeId(), request.reason()));
    }
}
