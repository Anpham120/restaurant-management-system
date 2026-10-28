package com.restaurant.core.finance;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.restaurant.core.order.OrderApiTestBase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** QR/SePay: tạo QR không làm PAID; webhook xác thực API key, chống trùng, lệch tiền, đến muộn. Chạy bằng login rc_webhook. */
class SepayWebhookIT extends OrderApiTestBase {

    private static long txSeq = System.currentTimeMillis();

    private ResultActions webhook(String auth, String body) throws Exception {
        var req = post("/api/webhooks/sepay").contentType(MediaType.APPLICATION_JSON).content(body);
        if (auth != null) {
            req = req.header("Authorization", auth);
        }
        return mvc.perform(req);
    }

    static String payload(long id, String code, String content, long amount, String type) {
        return """
                {"id":%d,"gateway":"Vietcombank","transactionDate":"2026-10-26 10:00:00","accountNumber":"0010000000355",
                 "code":%s,"content":"%s","transferType":"%s","transferAmount":%d,"accumulated":0,"subAccount":null,
                 "referenceCode":"FT%d","description":"chuyen tien"}"""
                .formatted(id, code == null ? "null" : "\"" + code + "\"", content, type, amount, id);
    }

    private String startQr(long invoice) throws Exception {
        ResultActions r = postJson(cashier, "/api/invoices/{id}/payments", "{\"method\":\"QR\"}", invoice)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("WAITING_TRANSFER"))
                .andExpect(jsonPath("$.data.paymentId").doesNotExist())
                .andExpect(jsonPath("$.data.qrImageUrl").value(containsString("https://qr.sepay.vn/img?acc=0010000000355&bank=Vietcombank")));
        String ref = read(r, "$.data.paymentReference");
        // tải lại hóa đơn vẫn có ảnh QR của yêu cầu đang chờ
        getAs(cashier, "/api/invoices/{id}", invoice)
                .andExpect(jsonPath("$.data.paymentRequests[-1].status").value("WAITING_TRANSFER"))
                .andExpect(jsonPath("$.data.paymentRequests[-1].qrImageUrl").value(containsString("des=" + ref)));
        return ref;
    }

    @Test
    void qrIsPaidOnlyAfterVerifiedWebhookAndDuplicatesAreIgnored() throws Exception {
        long[] s = servedAndCheckedOut(75000);
        String ref = startQr(s[2]);
        assertThat(data.invoiceStatus(s[2])).isEqualTo("PAYMENT_PENDING");
        long tx = ++txSeq;
        String body = payload(tx, ref, ref + " thanh toan", 75000, "in");
        webhook(null, body).andExpect(status().isUnauthorized());
        webhook("Apikey sai-khoa", body).andExpect(status().isUnauthorized());
        assertThat(data.invoiceStatus(s[2])).isEqualTo("PAYMENT_PENDING");

        webhook("Apikey " + SEPAY_API_KEY, body).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.result").value("PROCESSED"));
        webhook("Apikey " + SEPAY_API_KEY, body).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("PROCESSED"));
        assertThat(data.invoiceStatus(s[2])).isEqualTo("PAID");
        assertThat(data.count("SELECT count(*) FROM payments WHERE invoice_id = ?", s[2])).isEqualTo(1);
        assertThat(data.count("SELECT count(*) FROM payment_webhook_events WHERE provider_transaction_code = ?", String.valueOf(tx))).isEqualTo(1);
        getAs(waiter, "/api/sessions/{id}", s[0]).andExpect(jsonPath("$.data.status").value("CLOSED"));

        // chuyển khoản lần hai cho hóa đơn đã trả: lưu sự cố, không thu hai lần
        webhook("Apikey " + SEPAY_API_KEY, payload(++txSeq, ref, ref, 75000, "in"))
                .andExpect(jsonPath("$.data.result").value("AFTER_CANCEL"));
        assertThat(data.count("SELECT count(*) FROM payments WHERE invoice_id = ?", s[2])).isEqualTo(1);
    }

    @Test
    void wrongAmountIsMismatchAndInvoiceStaysPending() throws Exception {
        long[] s = servedAndCheckedOut(50000);
        String ref = startQr(s[2]);
        webhook("Apikey " + SEPAY_API_KEY, payload(++txSeq, ref, ref, 40000, "in"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.result").value("MISMATCH"));
        assertThat(data.invoiceStatus(s[2])).isEqualTo("PAYMENT_PENDING");
        assertThat(data.value("SELECT resolution_status FROM payment_webhook_events WHERE payment_reference = ?", ref)).isEqualTo("OPEN");
    }

    @Test
    void referenceIsFoundInContentWhenCodeMissing() throws Exception {
        long[] s = servedAndCheckedOut(30000);
        String ref = startQr(s[2]);
        webhook("Apikey " + SEPAY_API_KEY, payload(++txSeq, null, "NGUYEN VAN A " + ref.toLowerCase() + " chuyen tien", 30000, "in"))
                .andExpect(jsonPath("$.data.result").value("PROCESSED"));
        assertThat(data.invoiceStatus(s[2])).isEqualTo("PAID");
    }

    @Test
    void webhookAfterCancelledRequestOrAbandonIsStoredNotPaid() throws Exception {
        long[] s = servedAndCheckedOut(30000);
        String ref = startQr(s[2]);
        long requestId = data.count("SELECT id FROM payment_requests WHERE payment_reference = ?", ref);
        postJson(cashier, "/api/payment-requests/{id}/cancel", reason("Khách trả tiền mặt"), requestId).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentRequests[0].status").value("CANCELLED"));
        webhook("Apikey " + SEPAY_API_KEY, payload(++txSeq, ref, ref, 30000, "in"))
                .andExpect(jsonPath("$.data.result").value("AFTER_CANCEL"));
        assertThat(data.invoiceStatus(s[2])).isEqualTo("PAYMENT_PENDING");

        String ref2 = startQr(s[2]);
        postJson(manager, "/api/sessions/{id}/abandon", reason("Khách bỏ về"), s[0]).andExpect(status().isOk());
        webhook("Apikey " + SEPAY_API_KEY, payload(++txSeq, ref2, ref2, 30000, "in"))
                .andExpect(jsonPath("$.data.result").value("AFTER_CANCEL"));
        assertThat(data.invoiceStatus(s[2])).isEqualTo("UNCOLLECTED");
    }

    @Test
    void outgoingAndMalformedPayloads() throws Exception {
        long tx = ++txSeq;
        webhook("Apikey " + SEPAY_API_KEY, payload(tx, "RC1Q1", "RC1Q1", 1000, "out"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.result").value("IGNORED_OUTGOING"));
        assertThat(data.count("SELECT count(*) FROM payment_webhook_events WHERE provider_transaction_code = ?", String.valueOf(tx))).isZero();
        webhook("Apikey " + SEPAY_API_KEY, "{\"transferType\":\"in\"}").andExpect(status().isBadRequest());
        webhook("Apikey " + SEPAY_API_KEY, "khong-phai-json").andExpect(status().isBadRequest());
        webhook("Apikey " + SEPAY_API_KEY, payload(++txSeq, null, "khong co ma", 1000, "in"))
                .andExpect(jsonPath("$.data.result").value("MISMATCH"));
    }

    @Test
    void cashierCannotUseRcWebhookFunctionDirectly() throws Exception {
        long[] s = servedAndCheckedOut(10000);
        startQr(s[2]);
        // rc_app (ứng dụng) không có quyền gọi rc_process_verified_qr — chỉ login rc_webhook của SepayWebhookRepository
        assertThat(data.count("SELECT count(*) FROM pg_roles WHERE rolname = ? AND pg_has_role(rolname, 'rc_webhook', 'MEMBER') AND NOT pg_has_role(rolname, 'rc_app', 'MEMBER')",
                WEBHOOK_USER)).isEqualTo(1);
    }
}
