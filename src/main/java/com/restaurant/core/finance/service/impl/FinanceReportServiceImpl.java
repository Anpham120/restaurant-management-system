package com.restaurant.core.finance.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.restaurant.core.common.exception.ApiException;
import com.restaurant.core.common.time.BusinessDateProvider;
import com.restaurant.core.finance.dto.FinanceReportRows.ExpenseByCategory;
import com.restaurant.core.finance.dto.FinanceReportRows.RevenueByDay;
import com.restaurant.core.finance.dto.FinanceReportRows.RevenueByItem;
import com.restaurant.core.finance.dto.FinanceReportRows.UnpaidClosure;
import com.restaurant.core.finance.repository.FinanceReportRepository;
import com.restaurant.core.finance.repository.FinanceReportRepository.UnpaidInvoiceRow;
import com.restaurant.core.finance.service.FinanceReportService;

@Service
public class FinanceReportServiceImpl implements FinanceReportService {

    private static final int MAX_RANGE_DAYS = 366;

    private final FinanceReportRepository repo;

    public FinanceReportServiceImpl(FinanceReportRepository repo) {
        this.repo = repo;
    }

    @Override
    public List<RevenueByDay> getRevenueByDay(LocalDate from, LocalDate to) {
        checkRange(from, to);
        return repo.findRevenueByDay(from, to);
    }

    @Override
    public List<RevenueByItem> getRevenueByItem(LocalDate from, LocalDate to) {
        checkRange(from, to);
        return repo.findRevenueByItem(from, to);
    }

    @Override
    public List<UnpaidClosure> getUnpaidClosures(LocalDate from, LocalDate to) {
        checkRange(from, to);
        // gom theo (ngày kinh doanh lúc kết thúc, trạng thái); UNCOLLECTED/WAIVED không phải doanh thu
        Map<LocalDate, Map<String, List<UnpaidInvoiceRow>>> grouped = repo
                .findUnpaidInvoicesClosedBetween(BusinessDateProvider.startOf(from), BusinessDateProvider.startOf(to.plusDays(1))).stream()
                .collect(Collectors.groupingBy(r -> BusinessDateProvider.businessDateOf(r.closedAt()), TreeMap::new,
                        Collectors.groupingBy(UnpaidInvoiceRow::status, TreeMap::new, Collectors.toList())));
        List<UnpaidClosure> result = new ArrayList<>();
        grouped.forEach((day, byStatus) -> byStatus.forEach((status, rows) -> result.add(new UnpaidClosure(day, status, rows.size(),
                rows.stream().map(UnpaidInvoiceRow::total).reduce(BigDecimal.ZERO, BigDecimal::add)))));
        return result;
    }

    @Override
    public List<ExpenseByCategory> getExpensesByCategory(LocalDate from, LocalDate to) {
        checkRange(from, to);
        return repo.findExpensesByCategory(from, to);
    }

    static void checkRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to) || from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Khoảng ngày không hợp lệ (from ≤ to, tối đa " + MAX_RANGE_DAYS + " ngày)");
        }
    }
}
