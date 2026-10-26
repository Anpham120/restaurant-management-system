package com.restaurant.core.order;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.restaurant.core.TestData;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Bếp: từ chối, hết suất (cảnh báo khi gửi, REJECTED khi nhận), thiếu tồn, tiêu hao; hủy thường và hủy ngoại lệ. */
class KitchenAndCancelIT extends OrderApiTestBase {

    @Test
    void rejectRequiresReason() throws Exception {
        long item = submitOne(openTable()[2], data.dish(10000, null, null).dailyMenuItemId(), 1);
        postJson(kitchen, "/api/order-items/{id}/reject", "{\"reason\":\"\"}", item).andExpect(status().isBadRequest());
        postJson(kitchen, "/api/order-items/{id}/reject", reason("Hết món"), item).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
        postEmpty(kitchen, "/api/order-items/{id}/accept", item).andExpect(status().isConflict());
    }

    /** QĐ-4: hết suất KHÔNG chặn gửi món — trả cảnh báo; Kitchen nhận thì DB trả REJECTED kèm lý do. */
    @Test
    void quotaExceededIsWarningThenRejectedByKitchen() throws Exception {
        long[] t = openTable();
        long dmi = data.dish(30000, 1, null).dailyMenuItemId();
        long item = readLong(submit(t[2], "quota-1", dmi, 2).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.warnings[0].code").value("QUOTA_EXCEEDED"))
                .andExpect(jsonPath("$.data.warnings[0].remaining").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("PENDING")), "$.data.items[0].id");
        postEmpty(kitchen, "/api/order-items/{id}/accept", item).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("REJECTED"))
                .andExpect(jsonPath("$.data.reason").value(startsWith("Vượt hạn mức")))
                .andExpect(jsonPath("$.data.item.status").value("REJECTED"));
    }

    @Test
    void insufficientStockIsRejectedAndNothingReserved() throws Exception {
        TestData.Dish dish = data.dish(30000, null, 1);
        long item = submitOne(openTable()[2], dish.dailyMenuItemId(), 2);
        postEmpty(kitchen, "/api/order-items/{id}/accept", item)
                .andExpect(jsonPath("$.data.result").value("REJECTED"))
                .andExpect(jsonPath("$.data.reason").value(startsWith("Thiếu nguyên liệu")));
        assertThat(data.kitchenReserved(dish.kitchenTypeId())).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void acceptReservesAndCookingConsumesOnce() throws Exception {
        TestData.Dish dish = data.dish(30000, null, 5);
        long item = submitOne(openTable()[2], dish.dailyMenuItemId(), 2);
        postEmpty(kitchen, "/api/order-items/{id}/start-cooking", item).andExpect(status().isConflict());   // chưa ACCEPTED
        postEmpty(kitchen, "/api/order-items/{id}/accept", item).andExpect(jsonPath("$.data.result").value("ACCEPTED"));
        assertThat(data.kitchenReserved(dish.kitchenTypeId())).isEqualByComparingTo("2");
        postEmpty(kitchen, "/api/order-items/{id}/start-cooking", item).andExpect(status().isOk());
        postEmpty(kitchen, "/api/order-items/{id}/start-cooking", item).andExpect(status().isConflict());
        assertThat(data.kitchenOnHand(dish.kitchenTypeId())).isEqualByComparingTo("3");
        assertThat(data.kitchenReserved(dish.kitchenTypeId())).isEqualByComparingTo("0");
    }

    @Test
    void cancelBeforeCookingReleasesReservationOnce() throws Exception {
        TestData.Dish dish = data.dish(30000, null, 5);
        long item = submitOne(openTable()[2], dish.dailyMenuItemId(), 2);
        postEmpty(kitchen, "/api/order-items/{id}/accept", item);
        postJson(waiter, "/api/order-items/{id}/cancel", "{}", item).andExpect(status().isBadRequest());
        postJson(waiter, "/api/order-items/{id}/cancel", reason("Khách đổi món"), item).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        postJson(waiter, "/api/order-items/{id}/cancel", reason("lần 2"), item).andExpect(status().isConflict());
        assertThat(data.kitchenReserved(dish.kitchenTypeId())).isEqualByComparingTo("0");
        assertThat(data.kitchenOnHand(dish.kitchenTypeId())).isEqualByComparingTo("5");
    }

    /** Từ COOKING: hủy thường bị DB chặn; chỉ Manager hủy ngoại lệ, nguyên liệu đã nấu không hoàn. */
    @Test
    void cancelAfterCookingNeedsManagerAndKeepsConsumption() throws Exception {
        TestData.Dish dish = data.dish(30000, null, 5);
        long item = submitOne(openTable()[2], dish.dailyMenuItemId(), 1);
        postEmpty(kitchen, "/api/order-items/{id}/accept", item);
        postEmpty(kitchen, "/api/order-items/{id}/start-cooking", item);
        postJson(waiter, "/api/order-items/{id}/cancel", reason("Khách đổi ý"), item).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("Manager")));
        postJson(manager, "/api/order-items/{id}/cancel-exception", reason("Món bị cháy"), item).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        assertThat(data.kitchenOnHand(dish.kitchenTypeId())).isEqualByComparingTo("4");
        getAs(manager, "/api/order-items/{id}/events", item)
                .andExpect(jsonPath("$.data[3].fromStatus").value("COOKING"))
                .andExpect(jsonPath("$.data[3].reason").value("Món bị cháy"));
    }

    @Test
    void queueListsOnlyRequestedStatuses() throws Exception {
        long[] t = openTable();
        long a = submitOne(t[2], data.dish(10000, null, null).dailyMenuItemId(), 1);
        long b = submitOne(t[2], data.dish(10000, null, null).dailyMenuItemId(), 1);
        postEmpty(kitchen, "/api/order-items/{id}/accept", b);
        getAs(kitchen, "/api/kitchen/queue?status=ACCEPTED")
                .andExpect(jsonPath("$.data[?(@.id == " + b + ")]").exists())
                .andExpect(jsonPath("$.data[?(@.id == " + a + ")]").doesNotExist());
    }
}
