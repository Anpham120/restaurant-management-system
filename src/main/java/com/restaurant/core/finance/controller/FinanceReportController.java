package com.restaurant.core.finance.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.restaurant.core.common.response.ApiResponse;
import com.restaurant.core.finance.dto.FinanceReportRows.ExpenseByCategory;
import com.restaurant.core.finance.dto.FinanceReportRows.RevenueByDay;
import com.restaurant.core.finance.dto.FinanceReportRows.RevenueByItem;
import com.restaurant.core.finance.dto.FinanceReportRows.UnpaidClosure;
import com.restaurant.core.finance.service.FinanceReportService;

/** Báo cáo tài chính (5.2, 5.4–5.5). Khoảng ngày theo ngày kinh doanh (mốc 04:00). Owner: Bùi Đào Đức Anh. */
@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasRole('MANAGER')")
public class FinanceReportController {

    private final FinanceReportService service;

    public FinanceReportController(FinanceReportService service) {
        this.service = service;
    }

    @GetMapping("/revenue-by-day")
    public ApiResponse<List<RevenueByDay>> revenueByDay(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(service.revenueByDay(from, to));
    }

    @GetMapping("/revenue-by-item")
    public ApiResponse<List<RevenueByItem>> revenueByItem(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(service.revenueByItem(from, to));
    }

    @GetMapping("/unpaid-closures")
    public ApiResponse<List<UnpaidClosure>> unpaidClosures(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(service.unpaidClosures(from, to));
    }

    @GetMapping("/expenses-by-category")
    public ApiResponse<List<ExpenseByCategory>> expensesByCategory(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(service.expensesByCategory(from, to));
    }
}
