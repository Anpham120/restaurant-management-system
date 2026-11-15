package com.restaurant.core.order;

import java.math.BigDecimal;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.restaurant.core.IntegrationTestBase;
import com.restaurant.core.TestData;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Gọi REST của vòng đời đơn hàng bằng token thật của từng vai trò. Mỗi test lớp con có nhân viên riêng. */
public abstract class OrderApiTestBase extends IntegrationTestBase {

    protected TestData.Staff waiterStaff;
    protected String waiter;
    protected String kitchen;
    protected String cashier;
    protected String manager;

    @org.junit.jupiter.api.BeforeEach
    void tokens() throws Exception {
        waiterStaff = data.staff("WAITER");
        waiter = bearer(waiterStaff);
        kitchen = bearer(data.staff("KITCHEN"));
        cashier = bearer(data.staff("CASHIER"));
        manager = bearer(data.staff("MANAGER"));
    }

    protected ResultActions postJson(String token, String url, String body, Object... vars) throws Exception {
        return mvc.perform(post(url, vars).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected ResultActions postEmpty(String token, String url, Object... vars) throws Exception {
        return mvc.perform(post(url, vars).header("Authorization", token));
    }

    protected ResultActions getAs(String token, String url, Object... vars) throws Exception {
        return mvc.perform(get(url, vars).header("Authorization", token));
    }

    protected static <T> T read(ResultActions r, String path) throws Exception {
        return JsonPath.read(r.andReturn().getResponse().getContentAsString(), path);
    }

    protected static long readLong(ResultActions r, String path) throws Exception {
        return ((Number) read(r, path)).longValue();
    }

    /** Mở bàn mới → [tableId, sessionId, orderId]. */
    protected long[] openTable() throws Exception {
        long table = data.table();
        ResultActions r = postEmpty(waiter, "/api/tables/{id}/sessions", table);
        return new long[] {table, readLong(r, "$.data.sessionId"), readLong(r, "$.data.orderId")};
    }

    protected ResultActions submit(long orderId, String requestKey, long dailyMenuItemId, int quantity) throws Exception {
        return postJson(waiter, "/api/orders/{id}/batches", "{\"requestKey\":\"" + requestKey + "\",\"items\":[{\"dailyMenuItemId\":"
                + dailyMenuItemId + ",\"quantity\":" + quantity + "}]}", orderId);
    }

    /** Gửi 1 dòng, trả id dòng món. */
    protected long submitOne(long orderId, long dailyMenuItemId, int quantity) throws Exception {
        return readLong(submit(orderId, "k-" + System.nanoTime(), dailyMenuItemId, quantity), "$.data.items[0].id");
    }

    /** Nhận → nấu → xong → phục vụ qua REST. */
    protected void serveThrough(long itemId) throws Exception {
        postEmpty(kitchen, "/api/order-items/{id}/accept", itemId);
        postEmpty(kitchen, "/api/order-items/{id}/start-cooking", itemId);
        postEmpty(kitchen, "/api/order-items/{id}/finish", itemId);
        postEmpty(waiter, "/api/order-items/{id}/serve", itemId);
    }

    /** Bàn mới có 1 món đã phục vụ và hóa đơn PAYMENT_PENDING → [sessionId, orderId, invoiceId]. */
    protected long[] servedAndCheckedOut(long price) throws Exception {
        long[] t = openTable();
        serveThrough(submitOne(t[2], data.dish(price, null, null).dailyMenuItemId(), 1));
        long invoice = readLong(postEmpty(cashier, "/api/orders/{id}/invoice", t[2]), "$.data.invoiceId");
        return new long[] {t[1], t[2], invoice};
    }

    /** Chạy các lời gọi cùng lúc (mỗi lời gọi một kết nối DB riêng): tất cả cùng chờ một hiệu lệnh rồi mới chạy. */
    protected static <T> java.util.List<T> concurrently(java.util.List<java.util.concurrent.Callable<T>> calls) throws Exception {
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(calls.size());
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.List<java.util.concurrent.Future<T>> futures = new java.util.ArrayList<>();
            for (java.util.concurrent.Callable<T> c : calls) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return c.call();
                }));
            }
            start.countDown();
            java.util.List<T> results = new java.util.ArrayList<>();
            for (java.util.concurrent.Future<T> f : futures) {
                results.add(f.get(30, java.util.concurrent.TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    protected static String reason(String r) {
        return "{\"reason\":\"" + r + "\"}";
    }

    protected static BigDecimal dec(Object o) {
        return new BigDecimal(o.toString());
    }
}
