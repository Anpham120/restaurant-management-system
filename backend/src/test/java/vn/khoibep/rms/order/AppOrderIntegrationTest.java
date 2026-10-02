package vn.khoibep.rms.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import vn.khoibep.rms.IntegrationTest;

/** P4-04: orders from delivery apps entered by hand, paid by the app when the shipper takes them (FR-21, US-41, BR-47). */
class AppOrderIntegrationTest extends IntegrationTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    @Test
    void anAppOrderTakesTheAppPriceAndKeepsItsCode() throws Exception {
        long dish = appDish(Map.of("GRABFOOD", 79_000));
        String code = unique("gf");

        // US-41 AC1
        long orderId = readLong(appOrder("GRABFOOD", code).andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("TAKEAWAY"))
                .andExpect(jsonPath("$.channel").value("GRABFOOD"))
                .andExpect(jsonPath("$.appOrderCode").value(code.toUpperCase(Locale.ROOT)))
                .andExpect(jsonPath("$.tableId").value(nullValue())), "$.id");
        addDish(orderId, dish, 2);
        get("/api/orders/" + orderId, as("phucvu"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(79_000))
                .andExpect(jsonPath("$.total").value(158_000));
        get("/api/kitchen/items", as("bep"))
                .andExpect(jsonPath("$[?(@.orderId == %d)].appOrderCode", orderId)
                        .value(hasItem(code.toUpperCase(Locale.ROOT))))
                .andExpect(jsonPath("$[?(@.orderId == %d)].channel", orderId).value(hasItem("GRABFOOD")));
    }

    @Test
    void aCodeIsEnteredOnceInItsChannelUnlessCancelled() throws Exception {
        String code = unique("GF");
        long first = readLong(appOrder("GRABFOOD", code).andExpect(status().isCreated()), "$.id");

        // US-41 AC2
        appOrder("GRABFOOD", code.toLowerCase(Locale.ROOT)).andExpect(status().isConflict());
        appOrder("SHOPEEFOOD", code).andExpect(status().isCreated());
        post("/api/orders/" + first + "/cancel", as("phucvu"), null).andExpect(status().isOk());
        appOrder("GRABFOOD", code).andExpect(status().isCreated());

        post("/api/orders", as("phucvu"), Map.of("type", "TAKEAWAY", "channel", "GRABFOOD"))
                .andExpect(status().isBadRequest());
        post("/api/orders", as("phucvu"), Map.of("type", "TAKEAWAY", "channel", "GRABFOOD", "appOrderCode", " "))
                .andExpect(status().isBadRequest());
        post("/api/orders", as("phucvu"), Map.of("type", "DINE_IN", "tableId", newTable().id(), "channel", "GRABFOOD",
                "appOrderCode", unique("GF"))).andExpect(status().isBadRequest());
    }

    @Test
    void aDishWithoutAPriceOnTheAppIsNotOrderedThere() throws Exception {
        long dish = appDish(Map.of("GRABFOOD", 79_000));
        long orderId = readLong(appOrder("SHOPEEFOOD", unique("SP")), "$.id");

        // US-41 AC3
        post("/api/orders/" + orderId + "/items", as("phucvu"),
                Map.of("items", List.of(Map.of("menuItemId", dish, "quantity", 1)))).andExpect(status().isConflict());
    }

    @Test
    void theShipperTakesTheOrderOnceEveryDishIsDone() throws Exception {
        long dish = appDish(Map.of("GRABFOOD", 79_000));
        long orderId = readLong(appOrder("GRABFOOD", unique("GF")), "$.id");
        long itemId = addDish(orderId, dish, 2);

        // US-41 AC4
        post("/api/orders/" + orderId + "/handover", as("phucvu"), null).andExpect(status().isConflict());
        patch("/api/order-items/" + itemId + "/status", as("bep"), Map.of("status", "COOKING")).andExpect(status().isOk());
        patch("/api/order-items/" + itemId + "/status", as("bep"), Map.of("status", "READY")).andExpect(status().isOk());
        String today = LocalDate.now(VN).toString();
        long revenueBefore = grabFoodRevenue(today);
        long drawerBefore = readLong(get("/api/cash-shifts/current", as("thungan")), "$.expectedCash");
        post("/api/orders/" + orderId + "/handover", as("phucvu"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("GRABFOOD"))
                .andExpect(jsonPath("$.amount").value(158_000));

        get("/api/orders/" + orderId, as("phucvu"))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.items[0].status").value("SERVED"));
        assertThat(grabFoodRevenue(today) - revenueBefore).isEqualTo(158_000);
        assertThat(readLong(get("/api/cash-shifts/current", as("thungan")), "$.expectedCash")).isEqualTo(drawerBefore);
        // BR-46: the app paid by transfer.
        get("/api/orders/" + orderId + "/einvoice", as("thungan"))
                .andExpect(jsonPath("$.invoice.paymentMethod").value("CK"))
                .andExpect(jsonPath("$.invoice.total").value(158_000));
        post("/api/orders/" + orderId + "/handover", as("phucvu"), null).andExpect(status().isConflict());
    }

    @Test
    void anAppOrderIsNotPaidAtTheCounter() throws Exception {
        long dish = appDish(Map.of("SHOPEEFOOD", 75_000));
        long orderId = readLong(appOrder("SHOPEEFOOD", unique("SP")), "$.id");
        addDish(orderId, dish, 1);

        // US-41 AC5
        post("/api/orders/" + orderId + "/payments/cash", as("thungan"), Map.of("receivedAmount", 75_000))
                .andExpect(status().isConflict());
        post("/api/orders/" + orderId + "/payments/transfer", as("thungan"), null).andExpect(status().isConflict());
        long dineIn = openOrder(newTable().id());
        addDish(dineIn, newDish(10_000), 1);
        post("/api/orders/" + dineIn + "/handover", as("phucvu"), null).andExpect(status().isConflict());
        post("/api/orders", as("bep"), Map.of("type", "TAKEAWAY", "channel", "GRABFOOD", "appOrderCode", unique("GF")))
                .andExpect(status().isForbidden());
        post("/api/orders/" + orderId + "/handover", as("bep"), null).andExpect(status().isForbidden());
    }

    @Test
    void eachDishHasItsPriceOnEachApp() throws Exception {
        long categoryId = readLong(get("/api/categories", as("quanly")), "$[0].id");
        long dish = appDish(Map.of("GRABFOOD", 79_000, "SHOPEEFOOD", 81_000));
        get("/api/menu-items", as("phucvu"))
                .andExpect(jsonPath("$[?(@.id == %d)].appPrices.SHOPEEFOOD", dish).value(hasItem(81_000)));

        // FR-21.1: left out, the prices stay; sent, they replace the old ones.
        put("/api/menu-items/" + dish, as("quanly"), Map.of("categoryId", categoryId, "name", unique("Món"), "price",
                65_000)).andExpect(status().isOk()).andExpect(jsonPath("$.appPrices.GRABFOOD").value(79_000));
        put("/api/menu-items/" + dish, as("quanly"), Map.of("categoryId", categoryId, "name", unique("Món"), "price",
                65_000, "appPrices", Map.of("SHOPEEFOOD", 82_000))).andExpect(status().isOk())
                .andExpect(jsonPath("$.appPrices.SHOPEEFOOD").value(82_000))
                .andExpect(jsonPath("$.appPrices.GRABFOOD").doesNotExist());
        put("/api/menu-items/" + dish, as("quanly"), Map.of("categoryId", categoryId, "name", unique("Món"), "price",
                65_000, "appPrices", Map.of("GRABFOOD", -1))).andExpect(status().isBadRequest());
    }

    /** A dish at 65.000 đ in the restaurant, with these prices on the apps. */
    private long appDish(Map<String, Integer> appPrices) throws Exception {
        long categoryId = readLong(get("/api/categories", as("quanly")), "$[0].id");
        return readLong(post("/api/menu-items", as("quanly"), Map.of("categoryId", categoryId, "name", unique("Nem"),
                "price", 65_000, "appPrices", appPrices)).andExpect(status().isCreated()), "$.id");
    }

    private ResultActions appOrder(String channel, String code) throws Exception {
        return post("/api/orders", as("phucvu"), Map.of("type", "TAKEAWAY", "channel", channel, "appOrderCode", code));
    }

    private long grabFoodRevenue(String day) throws Exception {
        List<Number> amounts = read(get("/api/reports/summary?from=" + day + "&to=" + day, as("quanly"))
                .andExpect(status().isOk()), "$.byMethod[?(@.method == 'GRABFOOD')].amount");
        return amounts.isEmpty() ? 0 : amounts.getFirst().longValue();
    }
}
