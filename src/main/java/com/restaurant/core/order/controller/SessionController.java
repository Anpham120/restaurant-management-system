package com.restaurant.core.order.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.restaurant.core.common.response.ApiResponse;
import com.restaurant.core.order.dto.AbandonResponse;
import com.restaurant.core.order.dto.ReasonRequest;
import com.restaurant.core.order.dto.SessionResponse;
import com.restaurant.core.order.service.TableService;
import com.restaurant.core.security.CurrentUser;

/** Phiên phục vụ: xem, đóng phiên trống, khách bỏ về. Owner: Phạm Duy An. */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final TableService tableService;
    private final CurrentUser currentUser;

    public SessionController(TableService tableService, CurrentUser currentUser) {
        this.tableService = tableService;
        this.currentUser = currentUser;
    }

    @GetMapping("/{sessionId}")
    @PreAuthorize("hasAnyRole('MANAGER', 'WAITER', 'CASHIER')")
    public ApiResponse<SessionResponse> get(@PathVariable @Positive long sessionId) {
        return ApiResponse.ok(tableService.getSession(sessionId));
    }

    @PostMapping("/{sessionId}/close-empty")
    @PreAuthorize("hasAnyRole('WAITER', 'MANAGER')")
    public ApiResponse<SessionResponse> closeEmpty(@PathVariable @Positive long sessionId) {
        return ApiResponse.ok(tableService.closeEmptySession(sessionId, currentUser.employeeId()));
    }

    @PostMapping("/{sessionId}/abandon")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<AbandonResponse> abandon(@PathVariable @Positive long sessionId, @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(tableService.abandonSession(sessionId, currentUser.employeeId(), request.reason()));
    }
}
