package com.restaurant.core.finance.service;

import java.time.LocalDate;
import java.util.List;

import com.restaurant.core.finance.dto.FinanceReportRows.ExpenseByCategory;
import com.restaurant.core.finance.dto.FinanceReportRows.RevenueByDay;
import com.restaurant.core.finance.dto.FinanceReportRows.RevenueByItem;
import com.restaurant.core.finance.dto.FinanceReportRows.UnpaidClosure;

/** Báo cáo doanh thu, khoản không thu, chi theo nhóm. Owner: Bùi Đào Đức Anh. */
public interface FinanceReportService {

    List<RevenueByDay> getRevenueByDay(LocalDate from, LocalDate to);

    List<RevenueByItem> getRevenueByItem(LocalDate from, LocalDate to);

    List<UnpaidClosure> getUnpaidClosures(LocalDate from, LocalDate to);

    List<ExpenseByCategory> getExpensesByCategory(LocalDate from, LocalDate to);
}
