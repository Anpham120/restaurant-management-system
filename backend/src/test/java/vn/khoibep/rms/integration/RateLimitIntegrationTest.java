package vn.khoibep.rms.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import vn.khoibep.rms.IntegrationTest;

/** P3-01: BR-30 (QR requests and waiting dishes per table) and BR-31 (sign-in attempts per user name). */
class RateLimitIntegrationTest extends IntegrationTest {

    @Test
    void aTableSendsAtMostTenRequestsAMinute() throws Exception {
        TableRef table = newTable();
        long dish = newDish(20_000);
        for (int i = 0; i < 9; i++) {
            guestOrders(table.qrToken(), dish, 1).andExpect(status().isCreated());
        }
        // Calling a waiter uses the same allowance: this is the tenth request.
        post("/api/public/tables/" + table.qrToken() + "/requests", null, Map.of("type", "CALL_STAFF"))
                .andExpect(status().isOk());

        // US-14 AC5
        ResultActions blocked = guestOrders(table.qrToken(), dish, 1).andExpect(status().isTooManyRequests());
        String retryAfter = blocked.andReturn().getResponse().getHeader("Retry-After");
        assertThat(Long.parseLong(retryAfter)).isBetween(1L, 60L);
        assertThat(body(blocked)).contains("thử lại sau");
        // Another table is not affected.
        guestOrders(newTable().qrToken(), dish, 1).andExpect(status().isCreated());
    }

    @Test
    void aTableKeepsAtMostThirtyDishesWaitingForConfirmation() throws Exception {
        TableRef table = newTable();
        long dish = newDish(15_000);
        post("/api/public/tables/" + table.qrToken() + "/items", null,
                Map.of("items", Collections.nCopies(30, Map.of("menuItemId", dish, "quantity", 1))))
                .andExpect(status().isCreated());

        // US-14 AC4
        ResultActions full = guestOrders(table.qrToken(), dish, 1).andExpect(status().isConflict());
        assertThat(body(full)).contains("chờ nhân viên xác nhận");

        long orderId = readLong(get("/api/public/tables/" + table.qrToken(), null), "$.order.orderId");
        post("/api/orders/" + orderId + "/confirm-pending", as("phucvu"), null).andExpect(status().isOk());
        guestOrders(table.qrToken(), dish, 1).andExpect(status().isCreated());
    }

    @Test
    void aUserNameIsTriedAtMostTenTimesAMinute() throws Exception {
        String username = unique("khongco");
        for (int i = 0; i < 10; i++) {
            post("/api/auth/login", null, Map.of("username", username, "password", "sai-mat-khau"))
                    .andExpect(status().isUnauthorized());
        }

        // US-01 AC4. The same name in capitals counts too: user names are not case sensitive.
        ResultActions blocked = post("/api/auth/login", null,
                Map.of("username", username.toUpperCase(), "password", PASSWORD))
                .andExpect(status().isTooManyRequests());
        assertThat(blocked.andReturn().getResponse().getHeader("Retry-After")).isNotBlank();
        assertThat(body(blocked)).contains("Đăng nhập quá nhiều lần");
        // Other user names are not affected.
        post("/api/auth/login", null, Map.of("username", unique("khongco"), "password", "sai-mat-khau"))
                .andExpect(status().isUnauthorized());
    }
}
