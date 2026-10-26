package com.restaurant.core.order;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Kết thúc phiên: đóng phiên trống, khách bỏ về (UNCOLLECTED), miễn thu (WAIVED), hóa đơn 0 đồng. */
class SessionEndIT extends OrderApiTestBase {

    @Test
    void closeEmptySessionOnlyWithoutObligation() throws Exception {
        long[] t = openTable();
        long item = submitOne(t[2], data.dish(10000, null, null).dailyMenuItemId(), 1);
        postEmpty(waiter, "/api/sessions/{id}/close-empty", t[1]).andExpect(status().isConflict());
        postJson(waiter, "/api/order-items/{id}/cancel", reason("Khách đi"), item);
        postEmpty(waiter, "/api/sessions/{id}/close-empty", t[1]).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.orderStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.data.invoiceId").doesNotExist());
        getAs(waiter, "/api/tables/{id}", t[0]).andExpect(jsonPath("$.data.status").value("AVAILABLE"));
    }

    @Test
    void abandonRecordsUncollectedAndFreesTable() throws Exception {
        long[] t = openTable();
        serveThrough(submitOne(t[2], data.dish(45000, null, null).dailyMenuItemId(), 1));
        postJson(manager, "/api/sessions/{id}/abandon", reason("Khách bỏ về"), t[1]).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invoiceStatus").value("UNCOLLECTED"));
        long invoice = readLong(getAs(cashier, "/api/orders/{id}/invoice", t[2])
                .andExpect(jsonPath("$.data.status").value("UNCOLLECTED"))
                .andExpect(jsonPath("$.data.total").value(45000.0))
                .andExpect(jsonPath("$.data.uncollectedReason").value("Khách bỏ về"))
                .andExpect(jsonPath("$.data.payment").doesNotExist()), "$.data.invoiceId");
        postJson(cashier, "/api/invoices/{id}/payments", "{\"method\":\"CASH\",\"cashReceived\":50000}", invoice)
                .andExpect(status().isConflict());
        getAs(waiter, "/api/tables/{id}", t[0]).andExpect(jsonPath("$.data.status").value("AVAILABLE"));
        assertThat(data.count("SELECT count(*) FROM payments WHERE invoice_id = ?", invoice)).isZero();
    }

    @Test
    void waiveClosesPendingInvoiceWithoutPayment() throws Exception {
        long[] s = servedAndCheckedOut(60000);
        postJson(manager, "/api/orders/{id}/waive", "{}", s[1]).andExpect(status().isBadRequest());
        postJson(manager, "/api/orders/{id}/waive", reason("Món ra chậm"), s[1]).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAIVED"))
                .andExpect(jsonPath("$.data.total").value(60000.0))
                .andExpect(jsonPath("$.data.waivedReason").value("Món ra chậm"))
                .andExpect(jsonPath("$.data.payment").doesNotExist());
        postJson(manager, "/api/orders/{id}/waive", reason("lần 2"), s[1]).andExpect(status().isConflict());
        getAs(waiter, "/api/sessions/{id}", s[0]).andExpect(jsonPath("$.data.status").value("CLOSED"));
    }

    @Test
    void paidInvoiceCannotBeWaived() throws Exception {
        long[] s = servedAndCheckedOut(20000);
        postJson(cashier, "/api/invoices/{id}/payments", "{\"method\":\"CASH\",\"cashReceived\":20000}", s[2]).andExpect(status().isCreated());
        postJson(manager, "/api/orders/{id}/waive", reason("trả lại"), s[1]).andExpect(status().isConflict());
    }

    /** Món giá 0: không chốt được để thu (RC422) → Manager đóng bằng WAIVED tổng 0. */
    @Test
    void zeroAmountInvoiceIsClosedByWaive() throws Exception {
        long[] t = openTable();
        serveThrough(submitOne(t[2], data.dish(0, null, null).dailyMenuItemId(), 1));
        postEmpty(cashier, "/api/orders/{id}/invoice", t[2]).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("RC422"));
        postJson(manager, "/api/orders/{id}/waive", reason("Món tặng"), t[2]).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAIVED"))
                .andExpect(jsonPath("$.data.total").value(0.0));
        getAs(waiter, "/api/tables/{id}", t[0]).andExpect(jsonPath("$.data.status").value("AVAILABLE"));
    }
}
