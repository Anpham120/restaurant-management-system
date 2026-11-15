package com.restaurant.core.order;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.restaurant.core.TestData;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Bất biến của bếp và kho khi nhiều thao tác đến cùng lúc qua REST thật (mỗi request một kết nối PostgreSQL):
 * suất cuối và nguyên liệu cuối chỉ thuộc về một món; nấu và hủy không cùng thắng; đánh dấu lô hỏng không để lọt món
 * đang giữ lô hỏng mà không bị gắn cờ. Mỗi ca lặp nhiều vòng để tăng khả năng gặp đúng thứ tự xấu.
 */
class KitchenConcurrencyIT extends OrderApiTestBase {

    private static final int ROUNDS = 5;

    /** Kết quả gọi: mã HTTP + data.result (nếu có). */
    private record Call(int status, String result) {
    }

    private Callable<Call> call(String token, String url, String body, Object... vars) {
        return () -> {
            var req = post(url, vars).header("Authorization", token);
            if (body != null) {
                req = req.contentType(MediaType.APPLICATION_JSON).content(body);
            }
            MvcResult r = mvc.perform(req).andReturn();
            String json = r.getResponse().getContentAsString();
            String result = null;
            try {
                result = JsonPath.read(json, "$.data.result");
            } catch (RuntimeException ignored) {
                // không có data.result (lỗi, hoặc API không trả result)
            }
            return new Call(r.getResponse().getStatus(), result);
        };
    }

    private Callable<Call> accept(long item) {
        return call(kitchen, "/api/order-items/{id}/accept", null, item);
    }

    private String itemStatus(long item) {
        return data.value("SELECT status FROM order_items WHERE id = ?", item);
    }

    @Test
    void lastPortionGoesToExactlyOneItem() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            long dish = data.dish(30000, 1, null).dailyMenuItemId();             // còn đúng 1 suất
            long a = submitOne(openTable()[2], dish, 1);
            long b = submitOne(openTable()[2], dish, 1);
            List<Call> calls = concurrently(List.of(accept(a), accept(b)));
            assertThat(calls).extracting(Call::status).containsOnly(200);
            assertThat(calls).extracting(Call::result).containsExactlyInAnyOrder("ACCEPTED", "REJECTED");
            assertThat(data.count("SELECT count(*) FROM order_items WHERE daily_menu_item_id = ? AND status = 'ACCEPTED'", dish)).isEqualTo(1);
        }
    }

    @Test
    void lastIngredientGoesToExactlyOneItem() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            TestData.Dish dish = data.dish(30000, null, 1);                    // tồn bếp đúng 1 phần
            long a = submitOne(openTable()[2], dish.dailyMenuItemId(), 1);
            long b = submitOne(openTable()[2], dish.dailyMenuItemId(), 1);
            List<Call> calls = concurrently(List.of(accept(a), accept(b)));
            assertThat(calls).extracting(Call::result).containsExactlyInAnyOrder("ACCEPTED", "REJECTED");
            assertThat(data.kitchenReserved(dish.kitchenTypeId())).isEqualByComparingTo("1");
            assertThat(data.kitchenOnHand(dish.kitchenTypeId())).isEqualByComparingTo("1");
        }
    }

    /** Bắt đầu nấu và hủy trước khi nấu cùng lúc: đúng một bên thắng, nguyên liệu tiêu hao hoặc hoàn đúng một lần. */
    @Test
    void startCookingAndCancelNeverBothWin() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            TestData.Dish dish = data.dish(30000, null, 5);
            long item = submitOne(openTable()[2], dish.dailyMenuItemId(), 2);
            postEmpty(kitchen, "/api/order-items/{id}/accept", item);
            List<Call> calls = concurrently(List.of(
                    call(kitchen, "/api/order-items/{id}/start-cooking", null, item),
                    call(waiter, "/api/order-items/{id}/cancel", reason("Khách đổi món"), item)));
            assertThat(calls).extracting(Call::status).containsExactlyInAnyOrder(200, 409);
            assertThat(data.kitchenReserved(dish.kitchenTypeId())).isEqualByComparingTo("0");
            if ("COOKING".equals(itemStatus(item))) {
                assertThat(data.kitchenOnHand(dish.kitchenTypeId())).isEqualByComparingTo("3");     // tiêu hao 2 phần
            } else {
                assertThat(itemStatus(item)).isEqualTo("CANCELLED");
                assertThat(data.kitchenOnHand(dish.kitchenTypeId())).isEqualByComparingTo("5");     // không tiêu hao
            }
        }
    }

    /**
     * Quản lý đánh dấu lô bếp hỏng đúng lúc bếp nhận món dùng lô đó. Dù thứ tự nào: không còn giữ chỗ ACTIVE trên lô
     * hỏng của một món chưa bị gắn cờ "cần xử lý nguyên liệu" (has_stock_exception).
     */
    @Test
    void markBatchUnusableWhileAcceptingLeavesNoUnflaggedHolder() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            TestData.Dish dish = data.dish(30000, null, 5);
            long batch = data.count("SELECT id FROM kitchen_batches WHERE kitchen_ingredient_type_id = ?", dish.kitchenTypeId());
            long holder = submitOne(openTable()[2], dish.dailyMenuItemId(), 1);
            postEmpty(kitchen, "/api/order-items/{id}/accept", holder);               // món đã giữ lô từ trước
            long racer = submitOne(openTable()[2], dish.dailyMenuItemId(), 1);
            List<Call> calls = concurrently(List.of(
                    call(manager, "/api/stock-batches/KITCHEN/{id}/mark-unusable", reason("Ôi thiu"), batch),
                    accept(racer)));
            assertThat(calls.getFirst().status()).isEqualTo(200);
            assertThat(calls.get(1).result()).isIn("ACCEPTED", "REJECTED");
            assertThat(data.count("""
                    SELECT count(*) FROM stock_reservations r JOIN order_items oi ON oi.id = r.order_item_id
                     WHERE r.kitchen_batch_id = ? AND r.status = 'ACTIVE' AND NOT oi.has_stock_exception""", batch))
                    .as("món giữ lô hỏng phải được gắn cờ").isZero();
            assertThat(data.value("SELECT has_stock_exception::text FROM order_items WHERE id = ?", holder)).isEqualTo("true");
            BigDecimal reserved = data.kitchenReserved(dish.kitchenTypeId());
            assertThat(reserved).isLessThanOrEqualTo(data.kitchenOnHand(dish.kitchenTypeId()));
        }
    }
}
