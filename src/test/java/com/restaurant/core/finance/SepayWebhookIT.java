package com.restaurant.core.finance;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.restaurant.core.order.OrderApiTestBase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** QR/SePay: tạo QR không làm PAID; webhook xác thực API key, chống trùng, lệch tiền, đến muộn. */
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

    /** Đúng mã tham chiếu, đúng số tiền nhưng tiền vào tài khoản khác tài khoản in trên QR: không tính là đã thu. */
    @Test
    void transferToAnotherAccountIsMismatch() throws Exception {
        long[] s = servedAndCheckedOut(45000);
        String ref = startQr(s[2]);
        String otherAccount = payload(++txSeq, ref, ref, 45000, "in").replace("\"accountNumber\":\"0010000000355\"", "\"accountNumber\":\"9999888877\"");
        webhook("Apikey " + SEPAY_API_KEY, otherAccount)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.result").value("MISMATCH"));
        assertThat(data.invoiceStatus(s[2])).isEqualTo("PAYMENT_PENDING");
        assertThat(data.count("SELECT count(*) FROM payments WHERE invoice_id = ?", s[2])).isZero();
        assertThat(data.value("SELECT resolution_status FROM payment_webhook_events WHERE provider_transaction_code = ?", String.valueOf(txSeq)))
                .isEqualTo("OPEN");

        // tài khoản ảo (VA) của nhà hàng đến trong subAccount: vẫn hợp lệ
        String viaSubAccount = payload(++txSeq, ref, ref, 45000, "in").replace("\"accountNumber\":\"0010000000355\"", "\"accountNumber\":\"1234567890\"")
                .replace("\"subAccount\":null", "\"subAccount\":\"0010000000355\"");
        webhook("Apikey " + SEPAY_API_KEY, viaSubAccount).andExpect(jsonPath("$.data.result").value("PROCESSED"));
        assertThat(data.invoiceStatus(s[2])).isEqualTo("PAID");
    }

    /** Sự cố MISMATCH: Quản lý xem danh sách, chi tiết (kèm payload gốc), xử lý một lần; vai trò khác không được xem. */
    @Test
    void managerListsAndResolvesPaymentIncidents() throws Exception {
        long[] s = servedAndCheckedOut(60000);
        String ref = startQr(s[2]);
        long tx = ++txSeq;
        webhook("Apikey " + SEPAY_API_KEY, payload(tx, ref, ref, 50000, "in")).andExpect(jsonPath("$.data.result").value("MISMATCH"));
        long incident = data.count("SELECT id FROM payment_webhook_events WHERE provider_transaction_code = ?", String.valueOf(tx));

        getAs(manager, "/api/payment-incidents?size=100").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.items[?(@.id == " + incident + ")].invoiceId").value(hasItem((int) s[2])))
                .andExpect(jsonPath("$.data.items[?(@.id == " + incident + ")].rawPayload").value(hasItem(nullValue())));
        getAs(manager, "/api/payment-incidents/{id}", incident).andExpect(jsonPath("$.data.processingResult").value("MISMATCH"))
                .andExpect(jsonPath("$.data.amount").value(50000.0)).andExpect(jsonPath("$.data.amountDue").value(60000.0))
                .andExpect(jsonPath("$.data.rawPayload").value(containsString("\"transferAmount\":50000")));
        getAs(cashier, "/api/payment-incidents").andExpect(status().isForbidden());

        postJson(manager, "/api/payment-incidents/{id}/resolve", "{\"note\":\"Đã hoàn 50.000 đ cho khách\"}", incident)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.resolutionStatus").value("RESOLVED"))
                .andExpect(jsonPath("$.data.resolutionNote").value("Đã hoàn 50.000 đ cho khách"))
                .andExpect(jsonPath("$.data.resolvedByName").isNotEmpty());
        postJson(manager, "/api/payment-incidents/{id}/resolve", "{\"note\":\"lần hai\"}", incident).andExpect(status().isConflict());
        getAs(manager, "/api/payment-incidents?status=RESOLVED&size=100")
                .andExpect(jsonPath("$.data.items[?(@.id == " + incident + ")]").isNotEmpty());
        assertThat(data.invoiceStatus(s[2])).isEqualTo("PAYMENT_PENDING");      // xử lý sự cố không tự thu tiền
    }

    /** Webhook bị mất: Quản lý ghi nhận tay theo mã giao dịch SePay; webhook đến muộn cùng mã không thu lần hai. */
    @Test
    void manualTransferWhenWebhookIsLost() throws Exception {
        long[] s = servedAndCheckedOut(35000);
        String ref = startQr(s[2]);
        long requestId = data.count("SELECT id FROM payment_requests WHERE payment_reference = ?", ref);
        long tx = ++txSeq;
        postJson(cashier, "/api/payment-requests/{id}/manual-transfer",
                "{\"transactionCode\":\"" + tx + "\",\"amount\":35000,\"note\":\"Khớp sao kê\"}", requestId).andExpect(status().isForbidden());
        postJson(manager, "/api/payment-requests/{id}/manual-transfer",
                "{\"transactionCode\":\"" + tx + "\",\"amount\":35000,\"note\":\"Khớp sao kê 10:05\"}", requestId)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.result").value("PROCESSED"))
                .andExpect(jsonPath("$.data.invoice.status").value("PAID"));
        assertThat(data.value("SELECT raw_payload FROM payment_webhook_events WHERE provider_transaction_code = ?", String.valueOf(tx)))
                .contains("\"source\":\"MANUAL\"");
        webhook("Apikey " + SEPAY_API_KEY, payload(tx, ref, ref, 35000, "in")).andExpect(jsonPath("$.data.result").value("PROCESSED"));
        assertThat(data.count("SELECT count(*) FROM payments WHERE invoice_id = ?", s[2])).isEqualTo(1);
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

}
