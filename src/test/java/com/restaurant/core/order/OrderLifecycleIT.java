package com.restaurant.core.order;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Luồng chính: mở bàn → gửi món → bếp → phục vụ → chốt hóa đơn → tiền mặt → đóng phiên, trả bàn. */
class OrderLifecycleIT extends OrderApiTestBase {

    @Test
    void fullCashLifecycle() throws Exception {
        long[] t = openTable();
        long tableId = t[0], sessionId = t[1], orderId = t[2];
        getAs(waiter, "/api/tables/{id}", tableId).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OCCUPIED"))
                .andExpect(jsonPath("$.data.currentSessionId").value(sessionId))
                .andExpect(jsonPath("$.data.currentOrderId").value(orderId));

        long beef = data.dish(50000, null, 10).dailyMenuItemId();
        long tea = data.dish(15000, null, null).dailyMenuItemId();
        ResultActions batch = postJson(waiter, "/api/orders/{id}/batches", """
                {"requestKey":"life-1","items":[{"dailyMenuItemId":%d,"quantity":2,"note":"ít cay"},{"dailyMenuItemId":%d,"quantity":1}]}"""
                .formatted(beef, tea), orderId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.replayed").value(false))
                .andExpect(jsonPath("$.data.warnings", hasSize(0)))
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[*].status").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("PENDING"))))
                .andExpect(jsonPath("$.data.items[?(@.dailyMenuItemId == " + beef + ")].note").value(org.hamcrest.Matchers.hasItem("ít cay")));
        long batchId = readLong(batch, "$.data.batchId");
        List<Integer> ids = read(batch, "$.data.items[*].id");
        // gửi lặp cùng requestKey: trả đợt cũ, không ghi thêm dòng
        postJson(waiter, "/api/orders/{id}/batches", "{\"requestKey\":\"life-1\",\"items\":[{\"dailyMenuItemId\":" + tea + ",\"quantity\":5}]}", orderId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.batchId").value(batchId))
                .andExpect(jsonPath("$.data.replayed").value(true))
                .andExpect(jsonPath("$.data.items", hasSize(2)));

        getAs(kitchen, "/api/kitchen/queue?status=PENDING").andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + ids.get(0) + ")].tableCode").exists());
        for (int id : ids) {
            postEmpty(kitchen, "/api/order-items/{id}/accept", id).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.result").value("ACCEPTED"));
            postEmpty(kitchen, "/api/order-items/{id}/start-cooking", id).andExpect(jsonPath("$.data.status").value("COOKING"));
            postEmpty(kitchen, "/api/order-items/{id}/finish", id).andExpect(jsonPath("$.data.status").value("READY"));
            postEmpty(waiter, "/api/order-items/{id}/serve", id).andExpect(jsonPath("$.data.status").value("SERVED"));
        }
        getAs(waiter, "/api/order-items/{id}/events", ids.get(0)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].toStatus").value(org.hamcrest.Matchers.contains("PENDING", "ACCEPTED", "COOKING", "READY", "SERVED")))
                .andExpect(jsonPath("$.data[0].actorId").value(waiterStaff.employeeId()));

        ResultActions inv = postEmpty(cashier, "/api/orders/{id}/invoice", orderId).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.data.total").value(115000.0))
                .andExpect(jsonPath("$.data.lines", hasSize(2)));
        long invoiceId = readLong(inv, "$.data.invoiceId");
        postEmpty(cashier, "/api/orders/{id}/invoice", orderId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("RC409"));

        postJson(cashier, "/api/invoices/{id}/payments", "{\"method\":\"CASH\",\"cashReceived\":200000}", invoiceId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.amountDue").value(115000.0));
        postJson(cashier, "/api/invoices/{id}/payments", "{\"method\":\"CASH\",\"cashReceived\":200000}", invoiceId)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("RC409"));

        getAs(cashier, "/api/invoices/{id}", invoiceId)
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.payment.method").value("CASH"))
                .andExpect(jsonPath("$.data.paymentRequests[0].changeAmount").value(85000.0));
        getAs(waiter, "/api/sessions/{id}", sessionId)
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.orderStatus").value("CLOSED"))
                .andExpect(jsonPath("$.data.invoiceStatus").value("PAID"));
        getAs(waiter, "/api/tables/{id}", tableId)
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.currentSessionId").doesNotExist());
        getAs(waiter, "/api/tables/{id}/sessions?page=0&size=5", tableId)
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].sessionId").value(sessionId));
        assertThat(data.count("SELECT count(*) FROM payments WHERE invoice_id = ?", invoiceId)).isEqualTo(1);
    }

    @Test
    void callMoreIsBlockedWhilePaymentPendingUntilReopened() throws Exception {
        long[] s = servedAndCheckedOut(30000);
        long dmi = data.dish(20000, null, null).dailyMenuItemId();
        submit(s[1], "more-1", dmi, 1).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("RC409"));
        postJson(cashier, "/api/invoices/{id}/reopen", reason("Khách gọi thêm"), s[2])
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DRAFT"));
        submit(s[1], "more-2", dmi, 1).andExpect(status().isCreated());
        getAs(waiter, "/api/orders/{id}", s[1]).andExpect(jsonPath("$.data.items", hasSize(2)));
    }

    /** p_actor luôn từ token: field actorId/waiterId client tự gửi bị bỏ qua. */
    @Test
    void actorComesFromTokenNotFromBody() throws Exception {
        long[] t = openTable();
        ResultActions r = postJson(waiter, "/api/orders/{id}/batches", """
                {"requestKey":"spoof-1","actorId":1,"waiterId":1,"items":[{"dailyMenuItemId":%d,"quantity":1}]}"""
                .formatted(data.dish(10000, null, null).dailyMenuItemId()), t[2]).andExpect(status().isCreated());
        long item = readLong(r, "$.data.items[0].id");
        assertThat(data.count("SELECT actor_id FROM order_item_events WHERE order_item_id = ?", item)).isEqualTo(waiterStaff.employeeId());
        assertThat(data.count("SELECT waiter_id FROM order_batches b JOIN order_items i ON i.order_batch_id = b.id WHERE i.id = ?", item))
                .isEqualTo(waiterStaff.employeeId());
    }

    @Test
    void rolesAreEnforcedPerEndpoint() throws Exception {
        long[] t = openTable();
        long dmi = data.dish(10000, null, null).dailyMenuItemId();
        postJson(kitchen, "/api/orders/{id}/batches", "{\"requestKey\":\"r1\",\"items\":[{\"dailyMenuItemId\":" + dmi + ",\"quantity\":1}]}", t[2])
                .andExpect(status().isForbidden());
        long item = submitOne(t[2], dmi, 1);
        postEmpty(waiter, "/api/order-items/{id}/accept", item).andExpect(status().isForbidden());
        postEmpty(cashier, "/api/order-items/{id}/serve", item).andExpect(status().isForbidden());
        getAs(waiter, "/api/kitchen/queue").andExpect(status().isForbidden());
        postEmpty(waiter, "/api/orders/{id}/invoice", t[2]).andExpect(status().isForbidden());
        postJson(waiter, "/api/sessions/{id}/abandon", reason("x"), t[1]).andExpect(status().isForbidden());
        postJson(cashier, "/api/orders/{id}/waive", reason("x"), t[2]).andExpect(status().isForbidden());
        postJson(waiter, "/api/order-items/{id}/cancel-exception", reason("x"), item).andExpect(status().isForbidden());
        postEmpty(manager, "/api/order-items/{id}/accept", item).andExpect(status().isOk());   // Manager giám sát được
    }

    @Test
    void validationAndNotFound() throws Exception {
        long[] t = openTable();
        postJson(waiter, "/api/orders/{id}/batches", "{\"requestKey\":\"\",\"items\":[]}", t[2])
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        postJson(waiter, "/api/orders/{id}/batches", "{\"requestKey\":\"v1\",\"items\":[{\"dailyMenuItemId\":1,\"quantity\":0}]}", t[2])
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details[0].field").value("items[0].quantity"));
        submit(t[2], "v2", 999999999L, 1).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("RC409"));
        getAs(waiter, "/api/orders/{id}", 999999999L).andExpect(status().isNotFound());
        getAs(waiter, "/api/sessions/{id}", 999999999L).andExpect(status().isNotFound());
        getAs(kitchen, "/api/kitchen/queue?status=SERVED").andExpect(status().isBadRequest());
    }
}
