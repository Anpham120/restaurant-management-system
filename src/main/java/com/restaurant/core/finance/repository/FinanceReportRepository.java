package com.restaurant.core.finance.repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.restaurant.core.finance.dto.response.FinanceReportRows.ExpenseByCategory;
import com.restaurant.core.finance.dto.response.FinanceReportRows.RevenueByDay;
import com.restaurant.core.finance.dto.response.FinanceReportRows.RevenueByItem;

/**
 * Truy vấn CHỈ ĐỌC cho báo cáo tài chính. Doanh thu thực thu chỉ lấy từ payments (mỗi hóa đơn tối đa một giao dịch,
 * QR chỉ có khi webhook đã xác minh) — hóa đơn không thu được / miễn thu không phải doanh thu. Owner: Bùi Đào Đức Anh.
 */
@Repository
public class FinanceReportRepository {

    /** Hóa đơn kết thúc không thu tiền (khách bỏ về / miễn thu) và thời điểm kết thúc. */
    public record UnpaidInvoiceRow(String status, BigDecimal total, Instant closedAt) {
    }

    private final JdbcClient jdbc;

    public FinanceReportRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<RevenueByDay> findRevenueByDay(LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        SELECT p.business_date,
                               COALESCE(SUM(p.amount) FILTER (WHERE p.method = 'CASH'), 0) AS cash_amount,
                               COALESCE(SUM(p.amount) FILTER (WHERE p.method = 'QR'), 0) AS qr_amount,
                               SUM(p.amount) AS total_amount, count(*) AS paid_invoices
                          FROM payments p
                         WHERE p.business_date BETWEEN :f AND :t
                         GROUP BY p.business_date ORDER BY p.business_date""")
                .param("f", from).param("t", to).query(RevenueByDay.class).list();
    }

    /** Doanh thu và số suất theo món: dòng hóa đơn của hóa đơn đã thu trong kỳ, theo giá chụp lúc gọi. */
    public List<RevenueByItem> findRevenueByItem(LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        SELECT d.menu_item_id, max(ii.item_name) AS item_name, SUM(ii.quantity) AS servings, SUM(ii.line_total) AS revenue
                          FROM payments p
                          JOIN invoice_items ii ON ii.invoice_id = p.invoice_id
                          JOIN order_items oi ON oi.id = ii.order_item_id
                          JOIN daily_menu_items d ON d.id = oi.daily_menu_item_id
                         WHERE p.business_date BETWEEN :f AND :t
                         GROUP BY d.menu_item_id ORDER BY SUM(ii.line_total) DESC, d.menu_item_id""")
                .param("f", from).param("t", to).query(RevenueByItem.class).list();
    }

    public List<UnpaidInvoiceRow> findUnpaidInvoicesClosedBetween(Instant from, Instant to) {
        return jdbc.sql("""
                        SELECT status, total, COALESCE(uncollected_at, waived_at) AS closed_at
                          FROM invoices
                         WHERE status IN ('UNCOLLECTED', 'WAIVED')
                           AND COALESCE(uncollected_at, waived_at) >= :f AND COALESCE(uncollected_at, waived_at) < :t""")
                .param("f", Timestamp.from(from)).param("t", Timestamp.from(to)).query(UnpaidInvoiceRow.class).list();
    }

    public List<ExpenseByCategory> findExpensesByCategory(LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        SELECT category, count(*) AS expense_count, SUM(amount) AS amount
                          FROM operating_expenses
                         WHERE status = 'PAID' AND business_date BETWEEN :f AND :t
                         GROUP BY category ORDER BY SUM(amount) DESC, category""")
                .param("f", from).param("t", to).query(ExpenseByCategory.class).list();
    }
}
