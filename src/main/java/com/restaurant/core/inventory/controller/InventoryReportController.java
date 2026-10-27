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
import com.restaurant.core.inventory.dto.InventoryReportDtos.HandoverRow;
import com.restaurant.core.inventory.dto.InventoryReportDtos.KitchenLotRow;
import com.restaurant.core.inventory.dto.InventoryReportDtos.KitchenStockRow;
import com.restaurant.core.inventory.dto.InventoryReportDtos.PreparationYieldRow;
import com.restaurant.core.inventory.dto.InventoryReportDtos.RawLotRow;
import com.restaurant.core.inventory.dto.InventoryReportDtos.RawStockRow;
import com.restaurant.core.inventory.dto.InventoryReportDtos.StockHistoryRow;
import com.restaurant.core.inventory.dto.InventoryReportDtos.UnusableLotRow;
import com.restaurant.core.inventory.service.InventoryReportService;

/** Báo cáo kho (rpt_*) và danh sách lô để chọn khi lập chứng từ. Owner: Đỗ Tuấn Anh. */
@RestController
@RequestMapping("/api/inventory")
public class InventoryReportController {

    private final InventoryReportService service;

    public InventoryReportController(InventoryReportService service) {
        this.service = service;
    }

    @GetMapping("/raw-stock")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<List<RawStockRow>> rawStock() {
        return ApiResponse.ok(service.rawStock());
    }

    @GetMapping("/handover")
    @PreAuthorize("hasAnyRole('MANAGER', 'KITCHEN')")
    public ApiResponse<List<HandoverRow>> handover() {
        return ApiResponse.ok(service.handoverAndWip());
    }

    @GetMapping("/kitchen-stock")
    @PreAuthorize("hasAnyRole('MANAGER', 'KITCHEN')")
    public ApiResponse<List<KitchenStockRow>> kitchenStock() {
        return ApiResponse.ok(service.kitchenStock());
    }

    @GetMapping("/unusable-lots")
    @PreAuthorize("hasAnyRole('MANAGER', 'KITCHEN')")
    public ApiResponse<List<UnusableLotRow>> unusableLots() {
        return ApiResponse.ok(service.unusableLots());
    }

    @GetMapping("/history")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<List<StockHistoryRow>> history(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                                      @RequestParam(required = false) @Positive Long ingredientId) {
        return ApiResponse.ok(service.stockHistory(from, to, ingredientId));
    }

    @GetMapping("/preparation-yield")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<List<PreparationYieldRow>> preparationYield(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(service.preparationYield(from, to));
    }

    @GetMapping("/raw-lots")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<List<RawLotRow>> rawLots(@RequestParam(required = false) @Positive Long ingredientId) {
        return ApiResponse.ok(service.rawLots(ingredientId));
    }

    @GetMapping("/kitchen-lots")
    @PreAuthorize("hasAnyRole('MANAGER', 'KITCHEN')")
    public ApiResponse<List<KitchenLotRow>> kitchenLots(@RequestParam(required = false) @Positive Long typeId) {
        return ApiResponse.ok(service.kitchenLots(typeId));
    }
}
