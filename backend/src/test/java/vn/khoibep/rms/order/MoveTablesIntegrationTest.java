package vn.khoibep.rms.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import vn.khoibep.rms.IntegrationTest;

/** P1-01: tables put together and orders moved between tables (FR-04.5, FR-04.6, US-31, BR-04, BR-36). */
class MoveTablesIntegrationTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void tablesPutTogetherShareOneOrderAndEitherQrOrdersIntoIt() throws Exception {
        TableRef a = newTable();
        TableRef b = newTable();
        long dish = newDish(50_000);
        long orderId = openOrder(a.id());
        addDish(orderId, dish, 1);

        // US-31 AC1
        move(orderId, a, b)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableName").value(a.name() + " + " + b.name()));
        for (TableRef t : List.of(a, b)) {
            Map<String, Object> onPlan = onFloorPlan(t);
            assertThat(onPlan).containsEntry("status", "OCCUPIED")
                    .containsEntry("groupLabel", a.name() + " + " + b.name());
            assertThat(((Number) onPlan.get("openOrderId")).longValue()).isEqualTo(orderId);
        }
        assertThat(readLong(get("/api/public/tables/" + b.qrToken(), null), "$.order.orderId")).isEqualTo(orderId);
        guestOrders(b.qrToken(), dish, 2).andExpect(status().isCreated());
        get("/api/orders/" + orderId, as("phucvu")).andExpect(jsonPath("$.pendingCount").value(1));
    }

    @Test
    void movingKeepsTheBillAndFreesTheOldTables() throws Exception {
        TableRef a = newTable();
        TableRef b = newTable();
        TableRef c = newTable();
        long orderId = openOrder(a.id());
        addDish(orderId, newDish(200_000), 2);
        move(orderId, a, b).andExpect(status().isOk());
        post("/api/orders/" + orderId + "/adjustments", as("thungan"),
                Map.of("type", "DISCOUNT", "amount", 20_000, "reason", "WAIT")).andExpect(status().isCreated());
        String code = read(post("/api/orders/" + orderId + "/payments/transfer", as("thungan"), null), "$.reference");

        // US-31 AC2: from two tables to one; the bill, the discount and the transfer code stay.
        move(orderId, c)
                .andExpect(jsonPath("$.tableName").value(c.name()))
                .andExpect(jsonPath("$.total").value(380_000))
                .andExpect(jsonPath("$.discountTotal").value(20_000));
        assertThat(onFloorPlan(a)).containsEntry("status", "AVAILABLE");
        assertThat(onFloorPlan(b)).containsEntry("status", "AVAILABLE");
        assertThat(onFloorPlan(c)).containsEntry("status", "OCCUPIED").containsEntry("groupLabel", null);
        get("/api/public/tables/" + a.qrToken(), null).andExpect(jsonPath("$.order").doesNotExist());
        post("/api/orders/" + orderId + "/payments/transfer", as("thungan"), null)
                .andExpect(jsonPath("$.reference").value(code));

        // US-31 AC5: paying gives the table back.
        post("/api/orders/" + orderId + "/payments/cash", as("thungan"), Map.of("receivedAmount", 380_000))
                .andExpect(status().isOk());
        assertThat(onFloorPlan(c)).containsEntry("status", "AVAILABLE");
    }

    @Test
    void anOrderMovesOnlyToFreeTablesAndOnlyAtATable() throws Exception {
        TableRef a = newTable();
        TableRef b = newTable();
        long first = openOrder(a.id());
        openOrder(b.id());

        // US-31 AC3
        ResultActions taken = move(first, b).andExpect(status().isConflict());
        assertThat(body(taken)).contains("Bàn " + b.name() + " đang có đơn khác");
        post("/api/orders/" + first + "/tables", as("phucvu"), Map.of("tableIds", List.of()))
                .andExpect(status().isBadRequest());
        long takeaway = readLong(post("/api/orders", as("phucvu"), Map.of("type", "TAKEAWAY"))
                .andExpect(status().isCreated()), "$.id");
        move(takeaway, newTable()).andExpect(status().isConflict());
        // US-31 AC6: not the kitchen's job.
        post("/api/orders/" + first + "/tables", as("bep"), Map.of("tableIds", List.of(newTable().id())))
                .andExpect(status().isForbidden());
    }

    @Test
    void theKitchenSeesTheNewTable() throws Exception {
        TableRef a = newTable();
        TableRef c = newTable();
        long orderId = openOrder(a.id());
        long itemId = addDish(orderId, newDish(30_000), 1);

        // US-31 AC4
        move(orderId, c).andExpect(status().isOk());
        get("/api/kitchen/items", as("bep"))
                .andExpect(jsonPath("$[?(@.id == %d)].tableName", itemId).value(hasItem(c.name())));
    }

    @Test
    void theTablesOfAnOrderAreKeptAsHistory() throws Exception {
        TableRef a = newTable();
        TableRef b = newTable();
        TableRef c = newTable();
        long orderId = openOrder(a.id());
        move(orderId, a, b).andExpect(status().isOk());
        move(orderId, c).andExpect(status().isOk());

        // US-31 AC6: which table, held until when; only the last one still held.
        List<String> history = jdbc.queryForList(
                "select table_id || ':' || (released_at is null) from order_table where order_id = ? order by id",
                String.class, orderId);
        assertThat(history).containsExactly(a.id() + ":false", b.id() + ":false", c.id() + ":true");
    }

    private ResultActions move(long orderId, TableRef... tables) throws Exception {
        return post("/api/orders/" + orderId + "/tables", as("phucvu"),
                Map.of("tableIds", Arrays.stream(tables).map(TableRef::id).toList()));
    }

    private Map<String, Object> onFloorPlan(TableRef table) throws Exception {
        List<Map<String, Object>> plan = read(get("/api/tables", as("phucvu")), "$");
        return plan.stream().filter(t -> ((Number) t.get("id")).longValue() == table.id()).findFirst().orElseThrow();
    }
}
