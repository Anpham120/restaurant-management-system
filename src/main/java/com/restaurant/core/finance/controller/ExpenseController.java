package com.restaurant.core.finance.controller;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
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
import com.restaurant.core.finance.dto.ExpenseRequest;
import com.restaurant.core.finance.dto.ExpenseResponse;
import com.restaurant.core.finance.dto.ReasonRequest;
import com.restaurant.core.finance.service.ExpenseService;
import com.restaurant.core.security.CurrentUser;

/** Khoản chi hoạt động (5.1). Owner: Bùi Đào Đức Anh. */
@RestController
@RequestMapping("/api/expenses")
@PreAuthorize("hasRole('MANAGER')")
public class ExpenseController {

    private final ExpenseService service;
    private final CurrentUser currentUser;

    public ExpenseController(ExpenseService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ExpenseResponse> create(@Valid @RequestBody ExpenseRequest request) {
        return ApiResponse.ok(service.create(currentUser.employeeId(), request));
    }

    @GetMapping
    public ApiResponse<List<ExpenseResponse>> list(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                   @RequestParam(required = false)
                                                   @Pattern(regexp = "INGREDIENT|SUPPLIES|UTILITY|RENT|MAINTENANCE|OTHER") String category,
                                                   @RequestParam(required = false) @Pattern(regexp = "PAID|VOIDED") String status) {
        return ApiResponse.ok(service.expenses(from, to, category, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<ExpenseResponse> get(@PathVariable @Positive long id) {
        return ApiResponse.ok(service.expense(id));
    }

    /** Không sửa khoản chi: ghi sai thì hủy (VOIDED) và ghi khoản mới. */
    @PostMapping("/{id}/void")
    public ApiResponse<ExpenseResponse> voidExpense(@PathVariable @Positive long id, @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.voidExpense(id, currentUser.employeeId(), request.reason()));
    }
}
