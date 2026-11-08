package com.restaurant.core.inventory.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.restaurant.core.common.response.ApiResponse;
import com.restaurant.core.inventory.dto.response.InventoryReportRows.HandoverRow;
import com.restaurant.core.inventory.dto.response.InventoryReportRows.KitchenLotRow;
import com.restaurant.core.inventory.dto.response.InventoryReportRows.KitchenStockRow;
import com.restaurant.core.inventory.dto.response.InventoryReportRows.PreparationYieldRow;
import com.restaurant.core.inventory.dto.response.InventoryReportRows.RawLotRow;
import com.restaurant.core.inventory.dto.response.InventoryReportRows.RawStockRow;
import com.restaurant.core.inventory.dto.response.InventoryReportRows.StockHistoryRow;
import com.restaurant.core.inventory.dto.response.InventoryReportRows.UnusableLotRow;
import com.restaurant.core.inventory.service.InventoryReportService;

/** Báo cáo kho và danh sách lô để chọn khi lập chứng từ. Owner: Đỗ Tuấn Anh. */
@RestController
@RequestMapping("/api/inventory")
public class InventoryReportController {

    private final InventoryReportService service;

    public InventoryReportController(InventoryReportService service) {
        this.service = service;
    }

    @GetMapping("/raw-stock")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<List<RawStockRow>> getRawStock() {
        return ApiResponse.ok(service.getRawStock());
    }

    @GetMapping("/handover")
    @PreAuthorize("hasAnyRole('MANAGER', 'KITCHEN')")
    public ApiResponse<List<HandoverRow>> getHandoverAndWip() {
        return ApiResponse.ok(service.getHandoverAndWip());
    }

    @GetMapping("/kitchen-stock")
    @PreAuthorize("hasAnyRole('MANAGER', 'KITCHEN')")
    public ApiResponse<List<KitchenStockRow>> getKitchenStock() {
        return ApiResponse.ok(service.getKitchenStock());
    }

    @GetMapping("/unusable-lots")
    @PreAuthorize("hasAnyRole('MANAGER', 'KITCHEN')")
    public ApiResponse<List<UnusableLotRow>> getUnusableLots() {
        return ApiResponse.ok(service.getUnusableLots());
    }

    @GetMapping("/history")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<List<StockHistoryRow>> getStockHistory(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                                      @RequestParam(required = false) @Positive Long ingredientId) {
        return ApiResponse.ok(service.getStockHistory(from, to, ingredientId));
    }

    @GetMapping("/preparation-yield")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<List<PreparationYieldRow>> getPreparationYield(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(service.getPreparationYield(from, to));
    }

    @GetMapping("/raw-lots")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<List<RawLotRow>> getRawLots(@RequestParam(required = false) @Positive Long ingredientId) {
        return ApiResponse.ok(service.getRawLots(ingredientId));
    }

    @GetMapping("/kitchen-lots")
    @PreAuthorize("hasAnyRole('MANAGER', 'KITCHEN')")
    public ApiResponse<List<KitchenLotRow>> getKitchenLots(@RequestParam(required = false) @Positive Long typeId) {
        return ApiResponse.ok(service.getKitchenLots(typeId));
    }
}
