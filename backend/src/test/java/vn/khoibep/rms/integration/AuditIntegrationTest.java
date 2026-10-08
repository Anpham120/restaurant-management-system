package vn.khoibep.rms.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import vn.khoibep.rms.IntegrationTest;

/** P1-03: the audit log (FR-16, US-29, BR-34). */
class AuditIntegrationTest extends IntegrationTest {

    private static final String TODAY = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).toString();

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void cancellingOrRefusingADishLogsWhoWhatHowFarAndWhy() throws Exception {
        TableRef table = newTable();
        String dishName = unique("Món nhật ký");
        long dish = newDish(dishName, 45_000);
        long orderId = openOrder(table.id());
        long itemId = addDish(orderId, dish, 2);
        patch("/api/order-items/" + itemId + "/status", as("bep"), Map.of("status", "COOKING"))
                .andExpect(status().isOk());

        // US-29 AC1
        post("/api/order-items/" + itemId + "/cancel", as("quanly"), Map.of("reason", "Khách đổi món"))
                .andExpect(status().isOk());
        Map<String, Object> cancelled = only(entries(orderId));
        assertThat(cancelled).containsEntry("action", "ITEM_CANCELLED")
                .containsEntry("employeeName", fullName("quanly"))
                .containsEntry("tableName", table.name())
                .containsEntry("subject", dishName + " x2")
                .containsEntry("beforeValue", "COOKING")
                .containsEntry("afterValue", "CANCELLED")
                .containsEntry("amount", 90_000)
                .containsEntry("reason", "Khách đổi món");

        // A guest's dish refused by a waiter is logged too; the previous status tells it apart.
        ResultActions guest = guestOrders(newTable().qrToken(), dish, 1);
        long guestOrderId = readLong(guest, "$.order.orderId");
        List<Number> guestItems = read(guest, "$.order.items[*].id");
        post("/api/order-items/" + guestItems.getFirst() + "/cancel", as("phucvu"), Map.of("reason", "Hết nguyên liệu"))
                .andExpect(status().isOk());
        assertThat(only(entries(guestOrderId))).containsEntry("beforeValue", "PENDING")
                .containsEntry("employeeName", fullName("phucvu"))
                .containsEntry("reason", "Hết nguyên liệu");
    }

    @Test
    void aManualConfirmationIsLogged() throws Exception {
        long orderId = openOrder(newTable().id());
        addDish(orderId, newDish(70_000), 1);
        ResultActions transfer = post("/api/orders/" + orderId + "/payments/transfer", as("thungan"), null);

        // US-29 AC2
        post("/api/payments/" + readLong(transfer, "$.paymentId") + "/confirm", as("thungan"), null)
                .andExpect(status().isOk());
        assertThat(only(entries(orderId))).containsEntry("action", "MANUAL_CONFIRMATION")
                .containsEntry("employeeName", fullName("thungan"))
                .containsEntry("subject", read(transfer, "$.reference"))
                .containsEntry("amount", 70_000);
    }

    @Test
    void onlyAPriceChangeIsLogged() throws Exception {
        String dishName = unique("Món đổi giá");
        long dish = newDish(dishName, 45_000);
        long categoryId = readLong(get("/api/categories", as("quanly")), "$[0].id");
        Map<String, Object> request = new HashMap<>(Map.of("categoryId", categoryId, "name", dishName, "price", 45_000));

        // US-29 AC3: a new description alone is not logged; a new price is.
        request.put("description", "Thêm rau");
        put("/api/menu-items/" + dish, as("quanly"), request).andExpect(status().isOk());
        assertThat(priceChanges(dishName)).isEmpty();
        request.put("price", 50_000);
        put("/api/menu-items/" + dish, as("quanly"), request).andExpect(status().isOk());
        assertThat(priceChanges(dishName)).singleElement().satisfies(e -> assertThat(e)
                .containsEntry("beforeValue", "45000")
                .containsEntry("afterValue", "50000")
                .containsEntry("employeeName", fullName("quanly")));
    }

    @Test
    void aRefusedActionLeavesNoLine() throws Exception {
        long orderId = openOrder(newTable().id());
        long itemId = addDish(orderId, newDish(30_000), 1);
        patch("/api/order-items/" + itemId + "/status", as("bep"), Map.of("status", "COOKING"))
                .andExpect(status().isOk());

        // US-29 AC4: a waiter may not cancel a dish being cooked; a manager must give a reason.
        post("/api/order-items/" + itemId + "/cancel", as("phucvu"), Map.of("reason", "Thử")).andExpect(status().isForbidden());
        post("/api/order-items/" + itemId + "/cancel", as("quanly"), Map.of()).andExpect(status().isBadRequest());
        assertThat(entries(orderId)).isEmpty();
    }

    @Test
    void theLogCannotBeChangedOrDeletedEvenInSql() throws Exception {
        long orderId = openOrder(newTable().id());
        long itemId = addDish(orderId, newDish(20_000), 1);
        post("/api/order-items/" + itemId + "/cancel", as("phucvu"), null).andExpect(status().isOk());
        Number id = (Number) only(entries(orderId)).get("id");

        // US-29 AC5: the database itself refuses, whoever sends the statement.
        assertThatThrownBy(() -> jdbc.update("UPDATE audit_entry SET reason = 'sửa' WHERE id = ?", id.longValue()))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_entry WHERE id = ?", id.longValue()))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE audit_entry"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThat(only(entries(orderId))).containsEntry("reason", null);
    }

    @Test
    void onlyManagersReadTheLog() throws Exception {
        String today = "/api/audit-entries?from=" + TODAY + "&to=" + TODAY;
        // US-29 AC6
        for (String user : List.of("phucvu", "bep", "thungan")) {
            get(today, as(user)).andExpect(status().isForbidden());
        }
        get(today, null).andExpect(status().isUnauthorized());
        get(today, as("admin")).andExpect(status().isOk());
        get("/api/audit-entries?from=2026-01-01&to=2026-06-30", as("quanly")).andExpect(status().isBadRequest());
        get("/api/audit-entries?from=" + TODAY + "&to=2026-01-01", as("quanly")).andExpect(status().isBadRequest());
    }

    /** Today's lines of one order, as a manager sees them. */
    private List<Map<String, Object>> entries(long orderId) throws Exception {
        List<Map<String, Object>> all = read(get("/api/audit-entries?from=" + TODAY + "&to=" + TODAY, as("quanly"))
                .andExpect(status().isOk()), "$");
        return all.stream().filter(e -> e.get("orderId") != null && ((Number) e.get("orderId")).longValue() == orderId)
                .toList();
    }

    private List<Map<String, Object>> priceChanges(String dishName) throws Exception {
        List<Map<String, Object>> all = read(get("/api/audit-entries?from=" + TODAY + "&to=" + TODAY, as("quanly")), "$");
        return all.stream().filter(e -> "PRICE_CHANGED".equals(e.get("action")) && dishName.equals(e.get("subject")))
                .toList();
    }

    private static Map<String, Object> only(List<Map<String, Object>> entries) {
        assertThat(entries).hasSize(1);
        return entries.getFirst();
    }

    private String fullName(String username) throws Exception {
        return read(get("/api/auth/me", as(username)), "$.fullName");
    }
}
