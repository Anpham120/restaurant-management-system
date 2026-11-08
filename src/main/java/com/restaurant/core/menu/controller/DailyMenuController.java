package com.restaurant.core.menu.controller;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.restaurant.core.common.response.ApiResponse;
import com.restaurant.core.menu.dto.request.DailyMenuItemRequest;
import com.restaurant.core.menu.dto.response.DailyMenuItemResponse;
import com.restaurant.core.menu.dto.request.DailyMenuRequest;
import com.restaurant.core.menu.dto.response.DailyMenuResponse;
import com.restaurant.core.menu.dto.request.FillQuotasRequest;
import com.restaurant.core.menu.dto.response.FillQuotasResponse;
import com.restaurant.core.menu.dto.response.IngredientUsageRow;
import com.restaurant.core.menu.dto.request.QuotaRequest;
import com.restaurant.core.menu.dto.request.QuotasRequest;
import com.restaurant.core.menu.dto.request.ReasonRequest;
import com.restaurant.core.menu.service.DailyMenuService;
import com.restaurant.core.security.CurrentUser;

/** Thực đơn ngày, hạn mức, tạm dừng/mở bán. Owner: Lê Anh. */
@RestController
@RequestMapping("/api")
public class DailyMenuController {

    private final DailyMenuService service;
    private final CurrentUser currentUser;

    public DailyMenuController(DailyMenuService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    /** Hợp đồng với Order: thực đơn ngày kinh doanh hiện tại, kèm suất còn lại. */
    @GetMapping("/daily-menus/today")
    @PreAuthorize("hasAnyRole('MANAGER', 'WAITER', 'KITCHEN', 'CASHIER')")
    public ApiResponse<DailyMenuResponse> getTodayMenu() {
        return ApiResponse.ok(service.getTodayMenu());
    }

    @GetMapping("/daily-menus")
    @PreAuthorize("hasAnyRole('MANAGER', 'WAITER', 'KITCHEN', 'CASHIER')")
    public ApiResponse<DailyMenuResponse> getMenuByDate(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(service.getMenuByDate(date));
    }

    @GetMapping("/daily-menus/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'WAITER', 'KITCHEN', 'CASHIER')")
    public ApiResponse<DailyMenuResponse> getDailyMenuById(@PathVariable @Positive long id) {
        return ApiResponse.ok(service.getDailyMenuById(id));
    }

    @PostMapping("/daily-menus")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<DailyMenuResponse> createDailyMenu(@Valid @RequestBody DailyMenuRequest request) {
        return ApiResponse.ok(service.createDailyMenu(request.businessDate(), Boolean.TRUE.equals(request.fromCatalog())));
    }

    /** Nhập số suất cho nhiều món một lần (màn thực đơn ngày sửa trực tiếp trên bảng). */
    @PutMapping("/daily-menus/{id}/quotas")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<DailyMenuResponse> updateMenuQuotas(@PathVariable @Positive long id, @Valid @RequestBody QuotasRequest request) {
        return ApiResponse.ok(service.updateMenuQuotas(id, request.items()));
    }

    /** Điền hạn mức theo số suất đã bán của thực đơn trước (hoặc của ngày fromDate). */
    @PostMapping("/daily-menus/{id}/fill-quotas")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<FillQuotasResponse> fillQuotasFromSales(@PathVariable @Positive long id, @RequestBody(required = false) FillQuotasRequest request) {
        return ApiResponse.ok(service.fillQuotasFromSales(id, request == null ? null : request.fromDate()));
    }

    /** Tóm tắt cuối ngày phần nguyên liệu: dự kiến theo hạn mức, đã dùng khi nấu, tồn bếp cuối ngày. */
    @GetMapping("/daily-menus/{id}/ingredient-usage")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<List<IngredientUsageRow>> getIngredientUsage(@PathVariable @Positive long id) {
        return ApiResponse.ok(service.getIngredientUsage(id));
    }

    @PostMapping("/daily-menus/{id}/items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<DailyMenuResponse> addDishToMenu(@PathVariable @Positive long id, @Valid @RequestBody DailyMenuItemRequest request) {
        return ApiResponse.ok(service.addDishToMenu(id, request));
    }

    @PostMapping("/daily-menus/{id}/publish")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<DailyMenuResponse> publishDailyMenu(@PathVariable @Positive long id) {
        return ApiResponse.ok(service.publishDailyMenu(id));
    }

    @PostMapping("/daily-menus/{id}/close")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<DailyMenuResponse> closeDailyMenu(@PathVariable @Positive long id) {
        return ApiResponse.ok(service.closeDailyMenu(id));
    }

    @PutMapping("/daily-menu-items/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<DailyMenuItemResponse> updateDishQuota(@PathVariable @Positive long id, @Valid @RequestBody QuotaRequest request) {
        return ApiResponse.ok(service.updateDishQuota(id, request.quotaTotal()));
    }

    @DeleteMapping("/daily-menu-items/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<Void> removeDishFromMenu(@PathVariable @Positive long id) {
        service.removeDishFromMenu(id);
        return ApiResponse.ok(null);
    }

    /** Tạm dừng bán (hết nguyên liệu, bếp quá tải…): Waiter không gửi được, món PENDING bị từ chối khi bếp nhận. */
    @PostMapping("/daily-menu-items/{id}/pause")
    @PreAuthorize("hasAnyRole('KITCHEN', 'MANAGER')")
    public ApiResponse<DailyMenuItemResponse> pauseDish(@PathVariable @Positive long id, @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.pauseDish(id, currentUser.employeeId(), request.reason()));
    }

    @PostMapping("/daily-menu-items/{id}/resume")
    @PreAuthorize("hasAnyRole('KITCHEN', 'MANAGER')")
    public ApiResponse<DailyMenuItemResponse> resumeDish(@PathVariable @Positive long id) {
        return ApiResponse.ok(service.resumeDish(id));
    }
}
