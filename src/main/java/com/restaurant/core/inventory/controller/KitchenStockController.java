package com.restaurant.core.inventory.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
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
import com.restaurant.core.inventory.dto.KitchenStockDtos.AdjustmentRequest;
import com.restaurant.core.inventory.dto.KitchenStockDtos.AdjustmentResponse;
import com.restaurant.core.inventory.dto.KitchenStockDtos.MarkUnusableResponse;
import com.restaurant.core.inventory.dto.KitchenStockDtos.PreparationRequest;
import com.restaurant.core.inventory.dto.KitchenStockDtos.PreparationResponse;
import com.restaurant.core.inventory.dto.KitchenStockDtos.ReturnRequest;
import com.restaurant.core.inventory.dto.KitchenStockDtos.ReturnResponse;
import com.restaurant.core.inventory.dto.StockDocumentDtos.ReasonRequest;
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

    @GetMapping("/kitchen-preparations/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'KITCHEN')")
    public ApiResponse<PreparationResponse> preparation(@PathVariable @Positive long id) {
        return ApiResponse.ok(service.preparation(id));
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
    public ApiResponse<ReturnResponse> createReturn(@Valid @RequestBody ReturnRequest request) {
        return ApiResponse.ok(service.createReturn(currentUser.employeeId(), request));
    }

    @GetMapping("/stock-returns/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'KITCHEN')")
    public ApiResponse<ReturnResponse> stockReturn(@PathVariable @Positive long id) {
        return ApiResponse.ok(service.stockReturn(id));
    }

    @PostMapping("/stock-returns/{id}/confirm")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<ReturnResponse> confirmReturn(@PathVariable @Positive long id) {
        return ApiResponse.ok(service.confirmReturn(id, currentUser.employeeId()));
    }

    @PostMapping("/stock-adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('MANAGER', 'KITCHEN')")
    public ApiResponse<AdjustmentResponse> createAdjustment(@Valid @RequestBody AdjustmentRequest request) {
        return ApiResponse.ok(service.createAdjustment(currentUser.employeeId(), request));
    }

    @GetMapping("/stock-adjustments/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'KITCHEN')")
    public ApiResponse<AdjustmentResponse> adjustment(@PathVariable @Positive long id) {
        return ApiResponse.ok(service.adjustment(id));
    }

    @PostMapping("/stock-adjustments/{id}/confirm")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<AdjustmentResponse> confirmAdjustment(@PathVariable @Positive long id) {
        return ApiResponse.ok(service.confirmAdjustment(id, currentUser.employeeId()));
    }

    /** kind = RAW (lô kho) | KITCHEN (lô bếp). Trả số món đang giữ lô bị gắn cờ cần xử lý. */
    @PostMapping("/stock-batches/{kind}/{batchId}/mark-unusable")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<MarkUnusableResponse> markUnusable(@PathVariable @Pattern(regexp = "RAW|KITCHEN") String kind,
                                                          @PathVariable @Positive long batchId, @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.markUnusable(kind, batchId, currentUser.employeeId(), request.reason()));
    }
}
