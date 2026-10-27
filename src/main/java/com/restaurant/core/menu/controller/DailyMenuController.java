package com.restaurant.core.menu.controller;

import java.time.LocalDate;

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
import com.restaurant.core.menu.dto.DailyMenuItemRequest;
import com.restaurant.core.menu.dto.DailyMenuItemResponse;
import com.restaurant.core.menu.dto.DailyMenuRequest;
import com.restaurant.core.menu.dto.DailyMenuResponse;
import com.restaurant.core.menu.dto.QuotaRequest;
import com.restaurant.core.menu.dto.ReasonRequest;
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
    public ApiResponse<DailyMenuResponse> today() {
        return ApiResponse.ok(service.today());
    }

    @GetMapping("/daily-menus")
    @PreAuthorize("hasAnyRole('MANAGER', 'WAITER', 'KITCHEN', 'CASHIER')")
    public ApiResponse<DailyMenuResponse> byDate(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(service.byDate(date));
    }

    @GetMapping("/daily-menus/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'WAITER', 'KITCHEN', 'CASHIER')")
    public ApiResponse<DailyMenuResponse> get(@PathVariable @Positive long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping("/daily-menus")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<DailyMenuResponse> create(@Valid @RequestBody DailyMenuRequest request) {
        return ApiResponse.ok(service.create(request.businessDate()));
    }

    @PostMapping("/daily-menus/{id}/items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<DailyMenuResponse> addItem(@PathVariable @Positive long id, @Valid @RequestBody DailyMenuItemRequest request) {
        return ApiResponse.ok(service.addItem(id, request));
    }

    @PostMapping("/daily-menus/{id}/publish")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<DailyMenuResponse> publish(@PathVariable @Positive long id) {
        return ApiResponse.ok(service.publish(id));
    }

    @PostMapping("/daily-menus/{id}/close")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<DailyMenuResponse> close(@PathVariable @Positive long id) {
        return ApiResponse.ok(service.close(id));
    }

    @PutMapping("/daily-menu-items/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<DailyMenuItemResponse> updateQuota(@PathVariable @Positive long id, @Valid @RequestBody QuotaRequest request) {
        return ApiResponse.ok(service.updateQuota(id, request.quotaTotal()));
    }

    @DeleteMapping("/daily-menu-items/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<Void> remove(@PathVariable @Positive long id) {
        service.removeItem(id);
        return ApiResponse.ok(null);
    }

    /** Tạm dừng bán (hết nguyên liệu, bếp quá tải…): Waiter không gửi được, món PENDING bị từ chối khi bếp nhận. */
    @PostMapping("/daily-menu-items/{id}/pause")
    @PreAuthorize("hasAnyRole('KITCHEN', 'MANAGER')")
    public ApiResponse<DailyMenuItemResponse> pause(@PathVariable @Positive long id, @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.pause(id, currentUser.employeeId(), request.reason()));
    }

    @PostMapping("/daily-menu-items/{id}/resume")
    @PreAuthorize("hasAnyRole('KITCHEN', 'MANAGER')")
    public ApiResponse<DailyMenuItemResponse> resume(@PathVariable @Positive long id) {
        return ApiResponse.ok(service.resume(id));
    }
}
