package com.restaurant.core.finance.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.restaurant.core.common.exception.ApiException;
import com.restaurant.core.finance.dto.FinanceReportRows.ExpenseByCategory;
import com.restaurant.core.finance.dto.FinanceReportRows.RevenueByDay;
import com.restaurant.core.finance.dto.FinanceReportRows.RevenueByItem;
import com.restaurant.core.finance.dto.FinanceReportRows.UnpaidClosure;
import com.restaurant.core.finance.repository.FinanceReportRepository;

/** Báo cáo doanh thu, khoản không thu, chi theo nhóm. Owner: Bùi Đào Đức Anh. */
@Service
public class FinanceReportService {

    private static final int MAX_RANGE_DAYS = 366;

    private final FinanceReportRepository repo;

    public FinanceReportService(FinanceReportRepository repo) {
        this.repo = repo;
    }

    public List<RevenueByDay> revenueByDay(LocalDate from, LocalDate to) {
        checkRange(from, to);
        return repo.revenueByDay(from, to);
    }

    public List<RevenueByItem> revenueByItem(LocalDate from, LocalDate to) {
        checkRange(from, to);
        return repo.revenueByItem(from, to);
    }

    public List<UnpaidClosure> unpaidClosures(LocalDate from, LocalDate to) {
        checkRange(from, to);
        return repo.unpaidClosures(from, to);
    }

    public List<ExpenseByCategory> expensesByCategory(LocalDate from, LocalDate to) {
        checkRange(from, to);
        return repo.expensesByCategory(from, to);
    }

    static void checkRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to) || from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Khoảng ngày không hợp lệ (from ≤ to, tối đa " + MAX_RANGE_DAYS + " ngày)");
        }
    }
}
