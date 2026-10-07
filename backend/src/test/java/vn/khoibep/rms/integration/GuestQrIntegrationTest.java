package vn.khoibep.rms.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import vn.khoibep.rms.IntegrationTest;

/** US-05, US-09, US-14, US-15 and BR-09 to BR-11. */
class GuestQrIntegrationTest extends IntegrationTest {

    @Test
    void guestDishesWaitForStaffConfirmationBeforeTheKitchen() throws Exception {
        TableRef table = newTable();
        long dish = newDish(30_000);

        get("/api/public/tables/" + table.qrToken(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableName").value(table.name()))
                .andExpect(jsonPath("$.order").doesNotExist());

        ResultActions sent = guestOrders(table.qrToken(), dish, 2)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.order.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.order.pendingCount").value(1))
                .andExpect(jsonPath("$.order.canPay").value(false));
        long orderId = readLong(sent, "$.order.orderId");

        get("/api/kitchen/items", as("bep")).andExpect(jsonPath("$[?(@.orderId == %d)]", orderId).isEmpty());
        get("/api/tables", as("phucvu"))
                .andExpect(jsonPath("$[?(@.id == %d)].pendingCount", table.id()).value(hasItem(1)));

        post("/api/orders/" + orderId + "/confirm-pending", as("phucvu"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("WAITING"));

        get("/api/kitchen/items", as("bep"))
                .andExpect(jsonPath("$[?(@.orderId == %d)].status", orderId).value(hasItem("WAITING")));
        get("/api/public/tables/" + table.qrToken(), null)
                .andExpect(jsonPath("$.order.items[0].status").value("WAITING"))
                .andExpect(jsonPath("$.order.total").value(60_000))
                .andExpect(jsonPath("$.order.canPay").value(true));
    }

    @Test
    void laterSubmissionsJoinTheSameOrder() throws Exception {
        TableRef table = newTable();
        long dish = newDish(10_000);
        long first = readLong(guestOrders(table.qrToken(), dish, 1).andExpect(status().isCreated()), "$.order.orderId");
        guestOrders(table.qrToken(), dish, 3)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.order.orderId").value(first))
                .andExpect(jsonPath("$.order.items.length()").value(2));
    }

    @Test
    void rejectedDishShowsTheReasonToTheGuest() throws Exception {
        TableRef table = newTable();
        long itemId = readLong(guestOrders(table.qrToken(), newDish(45_000), 1), "$.order.items[0].id");

        post("/api/order-items/" + itemId + "/cancel", as("phucvu"), Map.of()).andExpect(status().isBadRequest());
        post("/api/order-items/" + itemId + "/cancel", as("phucvu"), Map.of("reason", "Món này hết nguyên liệu"))
                .andExpect(status().isOk());

        ResultActions view = get("/api/public/tables/" + table.qrToken(), null)
                .andExpect(jsonPath("$.order.items[0].status").value("CANCELLED"));
        assertThat(body(view)).contains("Món này hết nguyên liệu");
    }

    @Test
    void regeneratedQrCodeReplacesTheOldOne() throws Exception {
        TableRef table = newTable();
        String newToken = read(post("/api/tables/" + table.id() + "/qr-token", as("quanly"), null)
                .andExpect(status().isOk()), "$.qrToken");
        assertThat(newToken).isNotEqualTo(table.qrToken());

        ResultActions old = get("/api/public/tables/" + table.qrToken(), null).andExpect(status().isNotFound());
        assertThat(body(old)).contains("Mã QR không còn hiệu lực");
        get("/api/public/tables/" + newToken, null).andExpect(status().isOk());
    }

    @Test
    void guestViewShowsNoStaffInformation() throws Exception {
        TableRef table = newTable();
        addDish(openOrder(table.id()), newDish(20_000), 1);
        String json = body(get("/api/public/tables/" + table.qrToken(), null).andExpect(status().isOk()));
        assertThat(json).doesNotContain("createdBy", "Trần Thị Phục", "phucvu");
    }

    @Test
    void guestMenuHidesSoldOutDishes() throws Exception {
        long dish = newDish(12_000);
        get("/api/public/menu", null).andExpect(jsonPath("$[*].items[?(@.id == %d)]", dish).isNotEmpty());
        patch("/api/menu-items/" + dish + "/availability", as("bep"), Map.of("available", false))
                .andExpect(status().isOk());
        get("/api/public/menu", null).andExpect(jsonPath("$[*].items[?(@.id == %d)]", dish).isEmpty());
    }

    @Test
    void unknownQrCodeIsRejected() throws Exception {
        get("/api/public/tables/khong-ton-tai", null).andExpect(status().isNotFound());
        guestOrders("khong-ton-tai", newDish(10_000), 1).andExpect(status().isNotFound());
    }
}
