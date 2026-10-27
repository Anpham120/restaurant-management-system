package com.restaurant.core.order.controller;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
import com.restaurant.core.order.dto.OpenSessionResponse;
import com.restaurant.core.order.dto.PageResponse;
import com.restaurant.core.order.dto.SessionResponse;
import com.restaurant.core.order.dto.TableRequest;
import com.restaurant.core.order.dto.TableResponse;
import com.restaurant.core.order.service.TableService;
import com.restaurant.core.security.CurrentUser;

/** Quản lý bàn và phiên: danh sách, thêm/sửa/ngừng/mở lại bàn, mở phiên, lịch sử phiên. Owner: Phạm Duy An. */
@RestController
@RequestMapping("/api/tables")
public class TableController {

    private final TableService tableService;
    private final CurrentUser currentUser;

    public TableController(TableService tableService, CurrentUser currentUser) {
        this.tableService = tableService;
        this.currentUser = currentUser;
    }

    /** includeInactive=true (chỉ có tác dụng với Manager) để xem cả bàn ngừng sử dụng. */
    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'WAITER', 'CASHIER')")
    public ApiResponse<List<TableResponse>> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return ApiResponse.ok(tableService.listTables(includeInactive && currentUser.roles().contains("MANAGER")));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<TableResponse> create(@Valid @RequestBody TableRequest request) {
        return ApiResponse.ok(tableService.createTable(request));
    }

    @PutMapping("/{tableId}")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<TableResponse> update(@PathVariable @Positive long tableId, @Valid @RequestBody TableRequest request) {
        return ApiResponse.ok(tableService.updateTable(tableId, request));
    }

    @PostMapping("/{tableId}/deactivate")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<TableResponse> deactivate(@PathVariable @Positive long tableId) {
        return ApiResponse.ok(tableService.deactivateTable(tableId));
    }

    @PostMapping("/{tableId}/activate")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<TableResponse> activate(@PathVariable @Positive long tableId) {
        return ApiResponse.ok(tableService.activateTable(tableId));
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
