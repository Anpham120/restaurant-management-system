package com.restaurant.core.finance;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.restaurant.core.IntegrationTestBase;
import com.restaurant.core.TestData;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Strategy thanh toán trên hóa đơn thật (dựng bằng chuỗi hàm rc_*). */
class PaymentIT extends IntegrationTestBase {

    private long newInvoice() {
        return data.pendingInvoice(data.staff("WAITER"), data.staff("KITCHEN"), data.staff("CASHIER"));
    }

    private org.springframework.test.web.servlet.ResultActions pay(long invoice, String token, String body) throws Exception {
        return mvc.perform(post("/api/invoices/{id}/payments", invoice).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void cashStrategyPaysAndClosesInvoice() throws Exception {
        long invoice = newInvoice();
        pay(invoice, bearer(data.staff("CASHIER")), "{\"method\":\"CASH\",\"cashReceived\":100000}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.paymentId").isNumber());
        assertThat(data.invoiceStatus(invoice)).isEqualTo("PAID");
    }

    @Test
    void qrStrategyOnlyWaitsForWebhook() throws Exception {
        long invoice = newInvoice();
        pay(invoice, bearer(data.staff("CASHIER")), "{\"method\":\"QR\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("WAITING_TRANSFER"))
                .andExpect(jsonPath("$.data.paymentReference").value(startsWith("RC")))
                .andExpect(jsonPath("$.data.paymentId").doesNotExist());
        assertThat(data.invoiceStatus(invoice)).isEqualTo("PAYMENT_PENDING");
    }

    @Test
    void rulesFromDatabaseAndValidation() throws Exception {
        long invoice = newInvoice();
        String cashier = bearer(data.staff("CASHIER"));
        pay(invoice, cashier, "{\"method\":\"CASH\",\"cashReceived\":1000}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.error.code").value("RC422"));
        pay(invoice, cashier, "{\"method\":\"CASH\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        pay(invoice, cashier, "{}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details[0].field").value("method"));
        pay(invoice, bearer(data.staff("WAITER")), "{\"method\":\"CASH\",\"cashReceived\":100000}")
                .andExpect(status().isForbidden());
        assertThat(data.invoiceStatus(invoice)).isEqualTo("PAYMENT_PENDING");
    }
}
