package com.restaurant.core.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.restaurant.core.order.OrderApiTestBase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Khoản chi, đối soát tiền mặt (phiên bản, stale), báo cáo doanh thu chỉ từ payments. */
class FinanceIT extends OrderApiTestBase {

    private LocalDate businessDate() {
        return LocalDate.parse(data.value("SELECT rc_business_date(now())::text"));
    }

    private String expenseBody(String key, String fund, String method, int amount) {
        return "{\"requestKey\":\"" + key + "\",\"category\":\"SUPPLIES\",\"description\":\"Mua khăn giấy\",\"amount\":" + amount
                + ",\"paymentMethod\":\"" + method + "\",\"fundSource\":\"" + fund + "\"}";
    }

    private BigDecimal cashRevenue(LocalDate d) throws Exception {
        List<Object> v = read(getAs(manager, "/api/reports/revenue-by-day?from=" + d + "&to=" + d), "$.data[*].cashAmount");
        return v.isEmpty() ? BigDecimal.ZERO : dec(v.get(0));
    }

    @Test
    void expensesAreIdempotentAndOnlyVoidable() throws Exception {
        LocalDate bd = businessDate();
        String key = UUID.randomUUID().toString();
        ResultActions created = postJson(manager, "/api/expenses", expenseBody(key, "OWNER", "BANK_TRANSFER", 120000))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.businessDate").value(bd.toString()));
        long id = readLong(created, "$.data.id");
        assertThat(readLong(postJson(manager, "/api/expenses", expenseBody(key, "OWNER", "BANK_TRANSFER", 120000)), "$.data.id")).isEqualTo(id);
        postJson(manager, "/api/expenses", expenseBody(UUID.randomUUID().toString(), "CASH_DRAWER", "BANK_TRANSFER", 1000))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.error.code").value("INVALID_DATA"));
        postJson(manager, "/api/expenses", expenseBody(UUID.randomUUID().toString(), "OWNER", "CASH", 0)).andExpect(status().isBadRequest());
        postJson(cashier, "/api/expenses", expenseBody(UUID.randomUUID().toString(), "OWNER", "CASH", 1000)).andExpect(status().isForbidden());

        getAs(manager, "/api/expenses?from=" + bd + "&to=" + bd + "&category=SUPPLIES").andExpect(jsonPath("$.data[*].id", hasItem((int) id)));
        List<Object> before = read(getAs(manager, "/api/reports/expenses-by-category?from=" + bd + "&to=" + bd), "$.data[?(@.category == 'SUPPLIES')].amount");

        postJson(manager, "/api/expenses/{id}/void", reason("ghi nhầm"), id).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VOIDED")).andExpect(jsonPath("$.data.voidReason").value("ghi nhầm"));
        postJson(manager, "/api/expenses/{id}/void", reason("lần nữa"), id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_VOIDED"));
        List<Object> after = read(getAs(manager, "/api/reports/expenses-by-category?from=" + bd + "&to=" + bd), "$.data[?(@.category == 'SUPPLIES')].amount");
        BigDecimal afterAmount = after.isEmpty() ? BigDecimal.ZERO : dec(after.get(0));
        assertThat(dec(before.get(0)).subtract(afterAmount)).isEqualByComparingTo("120000");
        getAs(manager, "/api/expenses?from=" + bd + "&to=" + bd.minusDays(1)).andExpect(status().isBadRequest());
    }

    @Test
    void revenueReconciliationAndStale() throws Exception {
        LocalDate bd = businessDate();
        BigDecimal cashBefore = cashRevenue(bd);

        long[] paid = servedAndCheckedOut(50000);
        postJson(cashier, "/api/invoices/{id}/payments", "{\"method\":\"CASH\",\"cashReceived\":100000}", paid[2]).andExpect(status().isCreated());
        assertThat(cashRevenue(bd).subtract(cashBefore)).isEqualByComparingTo("50000");
        getAs(manager, "/api/reports/revenue-by-item?from=" + bd + "&to=" + bd).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThan(0)));

        long[] waived = servedAndCheckedOut(30000);
        postJson(manager, "/api/orders/{id}/waive", reason("khách VIP"), waived[1]).andExpect(status().isOk());
        getAs(manager, "/api/reports/unpaid-closures?from=" + bd + "&to=" + bd)
                .andExpect(jsonPath("$.data[*].status", hasItem("WAIVED")));
        assertThat(cashRevenue(bd).subtract(cashBefore)).isEqualByComparingTo("50000");      // miễn thu không phải doanh thu

        // đối soát: khớp → v1; lập lại thiếu revisionReason → CHECK; lệch thiếu lý do → CHECK
        ResultActions summary = getAs(manager, "/api/cash-reconciliations/{d}", bd).andExpect(jsonPath("$.data.reconVersion").doesNotExist());
        BigDecimal sales = dec(read(summary, "$.data.cashSales"));
        BigDecimal expenses = dec(read(summary, "$.data.cashExpenses"));
        BigDecimal expected = new BigDecimal("500000").add(sales).subtract(expenses);
        String ok = "{\"businessDate\":\"" + bd + "\",\"openingCash\":500000,\"actualCash\":" + expected.toPlainString();
        postJson(manager, "/api/cash-reconciliations", ok + "}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reconVersion").value(1)).andExpect(jsonPath("$.data.difference").value(0.0))
                .andExpect(jsonPath("$.data.stale").value(false));
        postJson(manager, "/api/cash-reconciliations", ok + "}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.error.code").value("INVALID_DATA"));
        String short10k = "{\"businessDate\":\"" + bd + "\",\"openingCash\":500000,\"actualCash\":" + expected.subtract(new BigDecimal("10000")).toPlainString()
                + ",\"revisionReason\":\"đếm lại\"";
        postJson(manager, "/api/cash-reconciliations", short10k + "}").andExpect(status().isUnprocessableContent());
        postJson(manager, "/api/cash-reconciliations", short10k + ",\"reason\":\"thối nhầm\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reconVersion").value(2)).andExpect(jsonPath("$.data.difference").value(-10000.0))
                .andExpect(jsonPath("$.data.versions.length()").value(2))
                .andExpect(jsonPath("$.data.versions[0].status").value("CURRENT"))
                .andExpect(jsonPath("$.data.versions[1].status").value("SUPERSEDED"));

        // chi quỹ quầy sau khi đối soát → stale
        postJson(manager, "/api/expenses", expenseBody(UUID.randomUUID().toString(), "CASH_DRAWER", "CASH", 20000)).andExpect(status().isCreated());
        getAs(manager, "/api/cash-reconciliations/{d}", bd).andExpect(jsonPath("$.data.stale").value(true));

        postJson(manager, "/api/cash-reconciliations", "{\"businessDate\":\"" + bd.plusDays(3) + "\",\"openingCash\":0,\"actualCash\":0}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.error.code").value("RC422"));
        postJson(cashier, "/api/cash-reconciliations", ok + "}").andExpect(status().isForbidden());
        getAs(waiter, "/api/reports/revenue-by-day?from=" + bd + "&to=" + bd).andExpect(status().isForbidden());
    }
}
