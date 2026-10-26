package com.restaurant.core.order.controller;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.restaurant.core.common.response.ApiResponse;
import com.restaurant.core.order.dto.OpenSessionResponse;
import com.restaurant.core.order.dto.PageResponse;
import com.restaurant.core.order.dto.SessionResponse;
import com.restaurant.core.order.dto.TableResponse;
import com.restaurant.core.order.service.TableService;
import com.restaurant.core.security.CurrentUser;

/** Bàn: danh sách, trạng thái, mở phiên, lịch sử phiên. Owner: Phạm Duy An. */
@RestController
@RequestMapping("/api/tables")
public class TableController {

    private final TableService tableService;
    private final CurrentUser currentUser;

    public TableController(TableService tableService, CurrentUser currentUser) {
        this.tableService = tableService;
        this.currentUser = currentUser;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'WAITER', 'CASHIER')")
    public ApiResponse<List<TableResponse>> list() {
        return ApiResponse.ok(tableService.listTables());
    }

    @GetMapping("/{tableId}")
    @PreAuthorize("hasAnyRole('MANAGER', 'WAITER', 'CASHIER')")
    public ApiResponse<TableResponse> get(@PathVariable @Positive long tableId) {
        return ApiResponse.ok(tableService.getTable(tableId));
    }

    /** Vai trò khớp ma trận rc_open_session ở docs/07 §10 (WAITER, MANAGER); DB kiểm lại bằng rc_authorize. */
    @PostMapping("/{tableId}/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('WAITER', 'MANAGER')")
    public ApiResponse<OpenSessionResponse> openSession(@PathVariable @Positive long tableId) {
        return ApiResponse.ok(tableService.openSession(tableId, currentUser.employeeId()));
    }

    @GetMapping("/{tableId}/sessions")
    @PreAuthorize("hasAnyRole('MANAGER', 'WAITER', 'CASHIER')")
    public ApiResponse<PageResponse<SessionResponse>> sessionHistory(@PathVariable @Positive long tableId,
                                                                    @RequestParam(defaultValue = "0") @Min(0) int page,
                                                                    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(tableService.sessionHistory(tableId, page, size));
    }
}
