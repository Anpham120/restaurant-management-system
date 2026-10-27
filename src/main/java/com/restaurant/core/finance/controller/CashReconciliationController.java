package com.restaurant.core.finance.controller;

import java.time.LocalDate;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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
import com.restaurant.core.finance.dto.CashReconciliationResponse;
import com.restaurant.core.finance.dto.ReconcileRequest;
import com.restaurant.core.finance.service.CashReconciliationService;
import com.restaurant.core.security.CurrentUser;

/** Đối soát tiền mặt cuối ngày (5.3). Owner: Bùi Đào Đức Anh. */
@RestController
@RequestMapping("/api/cash-reconciliations")
@PreAuthorize("hasRole('MANAGER')")
public class CashReconciliationController {

    private final CashReconciliationService service;
    private final CurrentUser currentUser;

    public CashReconciliationController(CashReconciliationService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CashReconciliationResponse> reconcile(@Valid @RequestBody ReconcileRequest request) {
        return ApiResponse.ok(service.reconcile(currentUser.employeeId(), request));
    }

    @GetMapping("/{businessDate}")
    public ApiResponse<CashReconciliationResponse> get(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return ApiResponse.ok(service.get(businessDate));
    }
}
