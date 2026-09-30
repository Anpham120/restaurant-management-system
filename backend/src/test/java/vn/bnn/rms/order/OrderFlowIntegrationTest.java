package vn.bnn.rms.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import vn.bnn.rms.IntegrationTest;

/** US-08, US-10, US-11, US-12, US-13 and BR-04 to BR-08. */
class OrderFlowIntegrationTest extends IntegrationTest {

    @Test
    void staffDishGoesThroughKitchenToTable() throws Exception {
        TableRef table = newTable();
        long dish = newDish(50_000);
        long orderId = openOrder(table.id());
        long itemId = addDish(orderId, dish, 2);

        get("/api/orders/" + orderId, as("phucvu"))
                .andExpect(jsonPath("$.total").value(100_000))
                .andExpect(jsonPath("$.items[0].status").value("WAITING"));
        get("/api/kitchen/items", as("bep"))
                .andExpect(jsonPath("$[?(@.id == %d)].status", itemId).value(hasItem("WAITING")))
                .andExpect(jsonPath("$[?(@.id == %d)].tableName", itemId).value(hasItem(table.name())));

        patch("/api/order-items/" + itemId + "/status", as("bep"), Map.of("status", "COOKING"))
                .andExpect(status().isOk());
        patch("/api/order-items/" + itemId + "/status", as("bep"), Map.of("status", "READY"))
                .andExpect(status().isOk());
        get("/api/tables", as("phucvu"))
                .andExpect(jsonPath("$[?(@.id == %d)].readyCount", table.id()).value(hasItem(1)));

        // Only a waiter confirms the dish reached the table.
        patch("/api/order-items/" + itemId + "/status", as("bep"), Map.of("status", "SERVED"))
                .andExpect(status().isForbidden());
        patch("/api/order-items/" + itemId + "/status", as("phucvu"), Map.of("status", "SERVED"))
                .andExpect(status().isOk());
        get("/api/kitchen/items", as("bep")).andExpect(jsonPath("$[?(@.id == %d)]", itemId).isEmpty());
    }

    @Test
    void tableHasAtMostOneOpenOrder() throws Exception {
        TableRef table = newTable();
        openOrder(table.id());
        get("/api/tables", as("phucvu"))
                .andExpect(jsonPath("$[?(@.id == %d)].status", table.id()).value(hasItem("OCCUPIED")));
        ResultActions second = post("/api/orders", as("phucvu"),
                Map.of("type", "DINE_IN", "tableId", table.id(), "guestCount", 2))
                .andExpect(status().isConflict());
        assertThat(body(second)).contains("Bàn đã có đơn đang mở");
    }

    @Test
    void statusCannotSkipSteps() throws Exception {
        long orderId = openOrder(newTable().id());
        long itemId = addDish(orderId, newDish(20_000), 1);
        patch("/api/order-items/" + itemId + "/status", as("bep"), Map.of("status", "READY"))
                .andExpect(status().isConflict());
    }

    @Test
    void soldOutDishCannotBeOrdered() throws Exception {
        long dish = newDish(35_000);
        long orderId = openOrder(newTable().id());
        patch("/api/menu-items/" + dish + "/availability", as("bep"), Map.of("available", false))
                .andExpect(status().isOk());
        ResultActions result = post("/api/orders/" + orderId + "/items", as("phucvu"),
                Map.of("items", java.util.List.of(Map.of("menuItemId", dish, "quantity", 1))))
                .andExpect(status().isConflict());
        assertThat(body(result)).contains("đã hết");
    }

    @Test
    void priceIsFrozenWhenOrdered() throws Exception {
        String name = unique("Món đổi giá");
        long dish = newDish(name, 40_000);
        long orderId = openOrder(newTable().id());
        addDish(orderId, dish, 1);
        long categoryId = readLong(get("/api/categories", as("quanly")), "$[0].id");
        put("/api/menu-items/" + dish, as("quanly"), Map.of("categoryId", categoryId, "name", name, "price", 55_000))
                .andExpect(status().isOk());
        get("/api/orders/" + orderId, as("phucvu")).andExpect(jsonPath("$.items[0].unitPrice").value(40_000));
    }

    @Test
    void whoMayCancelDependsOnHowFarTheDishWent() throws Exception {
        long orderId = openOrder(newTable().id());
        long dish = newDish(30_000);
        long waiting = addDish(orderId, dish, 1);
        long cooking = addDish(orderId, dish, 1);

        post("/api/order-items/" + waiting + "/cancel", as("phucvu"), Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        patch("/api/order-items/" + cooking + "/status", as("bep"), Map.of("status", "COOKING"))
                .andExpect(status().isOk());
        post("/api/order-items/" + cooking + "/cancel", as("phucvu"), Map.of("reason", "Khách đổi ý"))
                .andExpect(status().isForbidden());
        post("/api/order-items/" + cooking + "/cancel", as("quanly"), Map.of())
                .andExpect(status().isBadRequest());
        post("/api/order-items/" + cooking + "/cancel", as("quanly"), Map.of("reason", "Khách đổi ý"))
                .andExpect(status().isOk());
    }

    @Test
    void orderIsCancelledOnlyWhenEveryDishIsCancelled() throws Exception {
        TableRef table = newTable();
        long orderId = openOrder(table.id());
        long itemId = addDish(orderId, newDish(25_000), 1);

        post("/api/orders/" + orderId + "/cancel", as("phucvu"), null).andExpect(status().isConflict());
        post("/api/order-items/" + itemId + "/cancel", as("phucvu"), Map.of()).andExpect(status().isOk());
        post("/api/orders/" + orderId + "/cancel", as("phucvu"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        get("/api/tables", as("phucvu"))
                .andExpect(jsonPath("$[?(@.id == %d)].status", table.id()).value(hasItem("AVAILABLE")));
    }

    @Test
    void cashierCannotTakeOrdersAndChefCannotSeeTables() throws Exception {
        post("/api/orders", as("thungan"), Map.of("type", "TAKEAWAY")).andExpect(status().isForbidden());
        get("/api/tables", as("bep")).andExpect(status().isForbidden());
    }

    @Test
    void dishAlreadyOrderedCannotBeDeleted() throws Exception {
        long dish = newDish(15_000);
        addDish(openOrder(newTable().id()), dish, 1);
        ResultActions result = delete("/api/menu-items/" + dish, as("quanly")).andExpect(status().isConflict());
        assertThat(body(result)).contains("đánh dấu hết món");
    }
}
