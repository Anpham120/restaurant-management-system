package com.restaurant.core.finance.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.restaurant.core.finance.dto.FinanceReportRows.ExpenseByCategory;
import com.restaurant.core.finance.dto.FinanceReportRows.RevenueByDay;
import com.restaurant.core.finance.dto.FinanceReportRows.RevenueByItem;
import com.restaurant.core.finance.dto.FinanceReportRows.UnpaidClosure;

/** Báo cáo tài chính chỉ-đọc; doanh thu chỉ từ payments (rpt_*), không đụng lớp thanh toán. Owner: Bùi Đào Đức Anh. */
@Repository
public class FinanceReportRepository {

    private final JdbcClient jdbc;

    public FinanceReportRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<RevenueByDay> revenueByDay(LocalDate from, LocalDate to) {
        return jdbc.sql("SELECT * FROM rpt_revenue_by_day(:f, :t)").param("f", from).param("t", to).query(RevenueByDay.class).list();
    }

    public List<RevenueByItem> revenueByItem(LocalDate from, LocalDate to) {
        return jdbc.sql("SELECT * FROM rpt_revenue_by_item(:f, :t)").param("f", from).param("t", to).query(RevenueByItem.class).list();
    }

    public List<UnpaidClosure> unpaidClosures(LocalDate from, LocalDate to) {
        return jdbc.sql("SELECT * FROM rpt_unpaid_closures(:f, :t)").param("f", from).param("t", to).query(UnpaidClosure.class).list();
    }

    public List<ExpenseByCategory> expensesByCategory(LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        SELECT category, count(*) AS expense_count, SUM(amount) AS amount
                          FROM operating_expenses
                         WHERE status = 'PAID' AND business_date BETWEEN :f AND :t
                         GROUP BY category ORDER BY SUM(amount) DESC, category""")
                .param("f", from).param("t", to).query(ExpenseByCategory.class).list();
    }
}
