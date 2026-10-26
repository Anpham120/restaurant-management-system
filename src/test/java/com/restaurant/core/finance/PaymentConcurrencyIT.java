package com.restaurant.core.finance;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.restaurant.core.order.OrderApiTestBase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Nhiều request đồng thời qua REST thật (nhiều kết nối PostgreSQL): không thu hai lần, không mở trùng phiên. */
class PaymentConcurrencyIT extends OrderApiTestBase {

    private List<Integer> concurrently(List<Callable<Integer>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> c : calls) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return c.call();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : futures) {
                statuses.add(f.get());
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    private Callable<Integer> payCash(long invoice) {
        return () -> mvc.perform(post("/api/invoices/{id}/payments", invoice).header("Authorization", cashier)
                .contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"CASH\",\"cashReceived\":100000}")).andReturn().getResponse().getStatus();
    }

    private Callable<Integer> webhook(long tx, String ref, long amount) {
        return () -> mvc.perform(post("/api/webhooks/sepay").header("Authorization", "Apikey " + SEPAY_API_KEY)
                .contentType(MediaType.APPLICATION_JSON).content(SepayWebhookIT.payload(tx, ref, ref, amount, "in")))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void parallelCashPaymentsChargeOnce() throws Exception {
        for (int round = 0; round < 5; round++) {
            long[] s = servedAndCheckedOut(40000);
            List<Integer> st = concurrently(List.of(payCash(s[2]), payCash(s[2]), payCash(s[2])));
            assertThat(st).containsOnly(201, 409).filteredOn(x -> x == 201).hasSize(1);
            assertThat(data.count("SELECT count(*) FROM payments WHERE invoice_id = ?", s[2])).isEqualTo(1);
        }
    }

    @Test
    void duplicateWebhooksInParallelCreateOnePayment() throws Exception {
        for (int round = 0; round < 5; round++) {
            long[] s = servedAndCheckedOut(25000);
            String ref = read(postJson(cashier, "/api/invoices/{id}/payments", "{\"method\":\"QR\"}", s[2]), "$.data.paymentReference");
            long tx = System.nanoTime();
            List<Integer> st = concurrently(List.of(webhook(tx, ref, 25000), webhook(tx, ref, 25000), webhook(tx, ref, 25000), webhook(tx, ref, 25000)));
            assertThat(st).containsOnly(200);
            assertThat(data.count("SELECT count(*) FROM payments WHERE invoice_id = ?", s[2])).isEqualTo(1);
            assertThat(data.count("SELECT count(*) FROM payment_webhook_events WHERE provider_transaction_code = ?", String.valueOf(tx))).isEqualTo(1);
        }
    }

    /** Thu tiền mặt và webhook QR cùng lúc: đúng một bên thu được; bên kia RC409 / AFTER_CANCEL. */
    @Test
    void cashVersusWebhookNeverBothPay() throws Exception {
        for (int round = 0; round < 5; round++) {
            long[] s = servedAndCheckedOut(35000);
            String ref = read(postJson(cashier, "/api/invoices/{id}/payments", "{\"method\":\"QR\"}", s[2]), "$.data.paymentReference");
            long requestId = data.count("SELECT id FROM payment_requests WHERE payment_reference = ?", ref);
            // tiền mặt cần hủy yêu cầu QR trước (DB chặn thu tiền mặt khi còn QR mở) → đua: hủy+thu ↔ webhook
            Callable<Integer> cancelThenCash = () -> {
                int c = mvc.perform(post("/api/payment-requests/{id}/cancel", requestId).header("Authorization", cashier)
                        .contentType(MediaType.APPLICATION_JSON).content(reason("đổi sang tiền mặt"))).andReturn().getResponse().getStatus();
                return c == 200 ? payCash(s[2]).call() : c;
            };
            concurrently(List.of(cancelThenCash, webhook(System.nanoTime(), ref, 35000)));
            assertThat(data.count("SELECT count(*) FROM payments WHERE invoice_id = ?", s[2])).isEqualTo(1);
            assertThat(data.invoiceStatus(s[2])).isEqualTo("PAID");
        }
    }

    @Test
    void twoWaitersOpeningSameTable() throws Exception {
        long table = data.table();
        Callable<Integer> open = () -> mvc.perform(post("/api/tables/{id}/sessions", table).header("Authorization", waiter))
                .andReturn().getResponse().getStatus();
        assertThat(concurrently(List.of(open, open, open))).containsOnly(201, 409).filteredOn(x -> x == 201).hasSize(1);
        assertThat(data.count("SELECT count(*) FROM table_sessions WHERE dining_table_id = ? AND status = 'OPEN'", table)).isEqualTo(1);
    }
}
