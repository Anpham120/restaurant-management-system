package com.restaurant.core.order;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderStockAvailabilityIT extends OrderApiTestBase {

    @Test
    void insufficientStockRejectsWholeBatchAndKeepsPendingInvoice() throws Exception {
        long[] table = servedAndCheckedOut(30000);
        long order = table[1];
        long noRecipe = data.dish(15000, null, null).dailyMenuItemId();
        var dish = data.dish(30000, null, 1);

        postJson(waiter, "/api/orders/{id}/batches", """
                {"requestKey":"stock-short-batch","items":[{"dailyMenuItemId":%d,"quantity":1},{"dailyMenuItemId":%d,"quantity":2}]}
                """.formatted(noRecipe, dish.dailyMenuItemId()), order)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_STOCK"));

        getAs(waiter, "/api/orders/{id}", order).andExpect(jsonPath("$.data.items", hasSize(1)));
        assertThat(data.count("SELECT count(*) FROM order_batches WHERE request_key = ?", "stock-short-batch")).isZero();
        assertThat(data.invoiceStatus(table[2])).isEqualTo("PAYMENT_PENDING");
        assertThat(data.kitchenReserved(dish.kitchenTypeId())).isZero();
    }

    @Test
    void sharedIngredientDemandIncludesDifferentDishesAndRepeatedLines() throws Exception {
        long order = openTable()[2];
        var first = data.dish(30000, null, 3);
        long second = data.dishWithKitchenType(18000, null, first.kitchenTypeId()).dailyMenuItemId();

        postJson(waiter, "/api/orders/{id}/batches", """
                {"requestKey":"stock-shared-batch","items":[{"dailyMenuItemId":%d,"quantity":1},
                {"dailyMenuItemId":%d,"quantity":1},{"dailyMenuItemId":%d,"quantity":2}]}
                """.formatted(first.dailyMenuItemId(), second, first.dailyMenuItemId()), order)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_STOCK"));

        getAs(waiter, "/api/orders/{id}", order).andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @Test
    void concurrentPlacementsCannotBothPromiseLastPortion() throws Exception {
        var dish = data.dish(30000, null, 1);
        long firstDish = dish.dailyMenuItemId();
        long secondDish = data.dishWithKitchenType(18000, null, dish.kitchenTypeId()).dailyMenuItemId();
        long firstOrder = openTable()[2];
        long secondOrder = openTable()[2];
        List<MvcResult> calls = concurrently(List.of(
                () -> submit(firstOrder, "stock-race-first", firstDish, 1).andReturn(),
                () -> submit(secondOrder, "stock-race-second", secondDish, 1).andReturn()));
        assertThat(calls).extracting(call -> call.getResponse().getStatus()).containsExactlyInAnyOrder(201, 409);
        assertThat(data.kitchenReserved(dish.kitchenTypeId())).isZero();
        expectStockRemaining(firstDish, 0);
        expectStockRemaining(secondDish, 0);

        boolean firstWon = calls.getFirst().getResponse().getStatus() == 201;
        long winner = firstWon ? firstOrder : secondOrder;
        long winningDish = firstWon ? firstDish : secondDish;
        long otherDish = firstWon ? secondDish : firstDish;
        String winnerKey = firstWon ? "stock-race-first" : "stock-race-second";
        long item = readLong(submit(winner, winnerKey, winningDish, 1)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.replayed").value(true)), "$.data.items[0].id");
        postEmpty(kitchen, "/api/order-items/{id}/accept", item).andExpect(jsonPath("$.data.result").value("ACCEPTED"));
        expectStockRemaining(otherDish, 0);
        postJson(waiter, "/api/order-items/{id}/cancel", reason("Khách đổi món"), item).andExpect(status().isOk());
        expectStockRemaining(otherDish, 1);
    }

    @Test
    void expiredAndUnusableLotsDoNotCountAsAvailable() throws Exception {
        var expired = data.dish(25000, null, 5);
        data.count("UPDATE kitchen_batches SET usable_until = now() - interval '1 minute' WHERE kitchen_ingredient_type_id = ? RETURNING id",
                expired.kitchenTypeId());
        var unusable = data.dish(30000, null, 5);
        long unusableBatch = data.count("SELECT id FROM kitchen_batches WHERE kitchen_ingredient_type_id = ?", unusable.kitchenTypeId());
        postJson(manager, "/api/stock-batches/KITCHEN/{id}/mark-unusable", reason("Ôi thiu"), unusableBatch).andExpect(status().isOk());

        long order = openTable()[2];
        for (var dish : List.of(expired, unusable)) {
            expectStockRemaining(dish.dailyMenuItemId(), 0);
            submit(order, "stock-unusable-" + dish.dailyMenuItemId(), dish.dailyMenuItemId(), 1)
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("INSUFFICIENT_STOCK"));
        }
    }

    private void expectStockRemaining(long dailyMenuItemId, int expected) throws Exception {
        getAs(waiter, "/api/daily-menus/today").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.dailyMenuItemId == " + dailyMenuItemId + ")].stockRemaining", hasItem(expected)));
    }
}
