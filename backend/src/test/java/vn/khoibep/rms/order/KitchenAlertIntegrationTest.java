package vn.khoibep.rms.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import vn.khoibep.rms.IntegrationTest;
import vn.khoibep.rms.common.realtime.RealtimeEvent.Alert;
import vn.khoibep.rms.common.realtime.RealtimeEvent;

/** P1-06: which order changes make staff screens ring (FR-07.5), and the late-dish threshold (FR-07.4, BR-28). */
@RecordApplicationEvents
class KitchenAlertIntegrationTest extends IntegrationTest {

    @Autowired
    private ApplicationEvents events;

    @Test
    void kitchenRingsOnlyForDishesThatReachIt() throws Exception {
        TableRef table = newTable();
        long dish = newDish(30_000);

        // US-09 AC4: a guest dish rings for waiters. US-12 AC4: the kitchen rings once a waiter confirms it.
        long orderId = readLong(guestOrders(table.qrToken(), dish, 1).andExpect(status().isCreated()),
                "$.order.orderId");
        post("/api/orders/" + orderId + "/confirm-pending", as("phucvu"), null).andExpect(status().isOk());
        // A staff dish goes straight to the kitchen.
        addDish(orderId, dish, 1);

        assertThat(alerts(orderId)).containsExactly(Alert.GUEST_DISHES, Alert.NEW_DISHES, Alert.NEW_DISHES);
    }

    @Test
    void waitersHearWhenTheKitchenFinishesADish() throws Exception {
        long orderId = openOrder(newTable().id());
        long itemId = addDish(orderId, newDish(20_000), 1);
        for (String next : List.of("COOKING", "READY")) {
            patch("/api/order-items/" + itemId + "/status", as("bep"), Map.of("status", next))
                    .andExpect(status().isOk());
        }
        patch("/api/order-items/" + itemId + "/status", as("phucvu"), Map.of("status", "SERVED"))
                .andExpect(status().isOk());

        // US-10 AC3. Opening the order, starting to cook and serving ring nothing.
        assertThat(alerts(orderId)).containsExactly(null, Alert.NEW_DISHES, null, Alert.DISH_READY, null);
    }

    @Test
    void adminSetsTheLateDishThresholdThatTheKitchenReads() throws Exception {
        Map<String, Object> saved = read(get("/api/settings", as("admin")), "$");
        Map<String, Object> changed = new HashMap<>(saved);
        for (int invalid : List.of(0, 121)) {
            changed.put("waitAlertMinutes", invalid);
            put("/api/settings", as("admin"), changed).andExpect(status().isBadRequest());
        }
        changed.put("waitAlertMinutes", 20);
        put("/api/settings", as("quanly"), changed).andExpect(status().isForbidden());
        put("/api/settings", as("admin"), changed).andExpect(status().isOk());
        get("/api/settings", as("bep")).andExpect(jsonPath("$.waitAlertMinutes").value(20));

        // The settings row is shared by every test.
        put("/api/settings", as("admin"), saved).andExpect(status().isOk());
    }

    /** The alert of each ORDER_CHANGED notice of the order, oldest first; null when nothing should ring. */
    private List<Alert> alerts(long orderId) {
        return events.stream(RealtimeEvent.class)
                .filter(e -> RealtimeEvent.ORDER_CHANGED.equals(e.type()) && Objects.equals(e.orderId(), orderId))
                .map(RealtimeEvent::alert)
                .toList();
    }
}
