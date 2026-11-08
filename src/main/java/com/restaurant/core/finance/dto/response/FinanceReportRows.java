package com.restaurant.core.finance.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Dòng kết quả báo cáo tài chính: doanh thu theo ngày, theo món, hóa đơn kết thúc không thu tiền. */
public final class FinanceReportRows {

    private FinanceReportRows() {
    }

    public record RevenueByDay(LocalDate businessDate, BigDecimal cashAmount, BigDecimal qrAmount, BigDecimal totalAmount,
                               long paidInvoices) {
    }

    public record RevenueByItem(long menuItemId, String itemName, long servings, BigDecimal revenue) {
    }

    /** UNCOLLECTED (khách bỏ về) / WAIVED (miễn thu) — không phải doanh thu. */
    public record UnpaidClosure(LocalDate businessDate, String status, long invoiceCount, BigDecimal amount) {
    }

    /** Chi PAID theo nhóm trong kỳ (khoản đã hủy không tính). */
    public record ExpenseByCategory(String category, long expenseCount, BigDecimal amount) {
    }
}
