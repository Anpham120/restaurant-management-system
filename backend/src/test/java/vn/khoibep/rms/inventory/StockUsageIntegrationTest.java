package vn.khoibep.rms.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import vn.khoibep.rms.IntegrationTest;

/** P2-02: dish recipes, and the stock dishes take when they go to the kitchen (FR-09.8 → FR-09.10, US-33, BR-38). */
class StockUsageIntegrationTest extends IntegrationTest {

    private static final String TODAY = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).toString();

    @Test
    void dishesSentToTheKitchenTakeTheirIngredients() throws Exception {
        long beef = newItem(10);
        long noodles = newItem(10);
        String name = unique("Phở bò");
        long pho = newDish(name, 65_000);
        recipe(pho, Map.of(beef, 0.15, noodles, 0.3)).andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(2));
        TableRef table = newTable();
        long orderId = openOrder(table.id());

        // US-33 AC1
        addDish(orderId, pho, 2);
        assertThat(stock(beef)).isEqualTo(9.7);
        assertThat(stock(noodles)).isEqualTo(9.4);
        get("/api/inventory-items/" + beef + "/movements", as("quanly"))
                .andExpect(jsonPath("$[0].type").value("SALE"))
                .andExpect(jsonPath("$[0].quantityChange").value(-0.3))
                .andExpect(jsonPath("$[0].quantityAfter").value(9.7))
                .andExpect(jsonPath("$[0].note").value("Bàn " + table.name() + " · đơn #" + orderId + " · " + name + " × 2"));

        long takeaway = readLong(post("/api/orders", as("phucvu"), Map.of("type", "TAKEAWAY", "guestCount", 1))
                .andExpect(status().isCreated()), "$.id");
        addDish(takeaway, pho, 1);
        assertThat(stock(beef)).isEqualTo(9.55);
        get("/api/inventory-items/" + beef + "/movements", as("quanly"))
                .andExpect(jsonPath("$[0].note").value("Mang về · đơn #" + takeaway + " · " + name + " × 1"));

        // A dish without a recipe takes nothing.
        addDish(orderId, newDish(30_000), 4);
        assertThat(stock(beef)).isEqualTo(9.55);
    }

    @Test
    void guestDishesTakeNothingUntilConfirmed() throws Exception {
        long beef = newItem(5);
        long dish = newDish(120_000);
        recipe(dish, Map.of(beef, 0.2)).andExpect(status().isOk());

        // US-33 AC2
        ResultActions guest = guestOrders(newTable().qrToken(), dish, 3).andExpect(status().isCreated());
        assertThat(stock(beef)).isEqualTo(5.0);
        post("/api/orders/" + readLong(guest, "$.order.orderId") + "/confirm-pending", as("phucvu"), null)
                .andExpect(status().isOk());
        assertThat(stock(beef)).isEqualTo(4.4);
    }

    @Test
    void aDishCancelledBeforeCookingGivesItsIngredientsBack() throws Exception {
        long beef = newItem(10);
        long dish = newDish(90_000);
        recipe(dish, Map.of(beef, 0.2)).andExpect(status().isOk());
        long orderId = openOrder(newTable().id());
        long waiting = addDish(orderId, dish, 2);
        long cooking = addDish(orderId, dish, 1);
        assertThat(stock(beef)).isEqualTo(9.4);

        // US-33 AC5: a new recipe leaves what was already taken as it was.
        recipe(dish, Map.of(beef, 0.5)).andExpect(status().isOk());

        // US-33 AC3
        post("/api/order-items/" + waiting + "/cancel", as("phucvu"), Map.of()).andExpect(status().isOk());
        assertThat(stock(beef)).isEqualTo(9.8);
        get("/api/inventory-items/" + beef + "/movements", as("quanly"))
                .andExpect(jsonPath("$[0].type").value("SALE"))
                .andExpect(jsonPath("$[0].quantityChange").value(0.4))
                .andExpect(jsonPath("$[0].note").value(startsWith("Huỷ món, hoàn kho · Bàn ")));

        patch("/api/order-items/" + cooking + "/status", as("bep"), Map.of("status", "COOKING"))
                .andExpect(status().isOk());
        post("/api/order-items/" + cooking + "/cancel", as("quanly"), Map.of("reason", "Khách đổi món"))
                .andExpect(status().isOk());
        assertThat(stock(beef)).isEqualTo(9.8);
    }

    @Test
    void dishesMayTakeStockBelowZeroButHandsMayNot() throws Exception {
        long crab = newItem(0.5);
        long dish = newDish(45_000);
        recipe(dish, Map.of(crab, 0.3)).andExpect(status().isOk());

        // US-33 AC4
        addDish(openOrder(newTable().id()), dish, 3);
        assertThat(stock(crab)).isEqualTo(-0.4);
        get("/api/inventory-items", as("quanly"))
                .andExpect(jsonPath("$[?(@.id == %d)].lowStock", crab).value(hasItem(true)));
        post("/api/inventory-items/" + crab + "/movements", as("quanly"), Map.of("type", "OUT", "quantity", 0.1))
                .andExpect(status().isConflict());
        post("/api/inventory-items/" + crab + "/movements", as("quanly"), Map.of("type", "SALE", "quantity", 0.1))
                .andExpect(status().isBadRequest());

        // BR-37: with nothing left to cost, the price of the receipt is the cost.
        long supplier = readLong(post("/api/suppliers", as("quanly"), Map.of("name", unique("Chợ")))
                .andExpect(status().isCreated()), "$.id");
        post("/api/goods-receipts", as("quanly"), Map.of("supplierId", supplier, "lines",
                List.of(Map.of("inventoryItemId", crab, "quantity", 2, "unitPrice", 100_000))))
                .andExpect(status().isCreated());
        get("/api/inventory-items", as("quanly"))
                .andExpect(jsonPath("$[?(@.id == %d)].quantity", crab).value(hasItem(1.6)))
                .andExpect(jsonPath("$[?(@.id == %d)].unitCost", crab).value(hasItem(100_000)));
    }

    @Test
    void recipesAreCheckedAndReplacedWhole() throws Exception {
        long beef = newItem(10);
        long herbs = newItem(10);
        long dish = newDish(70_000);

        // US-33 AC5
        put("/api/menu-items/" + dish + "/recipe", as("quanly"), Map.of("lines", List.of(
                Map.of("inventoryItemId", beef, "quantity", 0.1), Map.of("inventoryItemId", beef, "quantity", 0.2))))
                .andExpect(status().isBadRequest());
        recipe(dish, Map.of(beef, 0)).andExpect(status().isBadRequest());
        recipe(dish, Map.of(-1L, 0.1)).andExpect(status().isNotFound());
        recipe(-1L, Map.of(beef, 0.1)).andExpect(status().isNotFound());
        put("/api/menu-items/" + dish + "/recipe", as("quanly"), Map.of("lines", IntStream.range(0, 31)
                .mapToObj(i -> Map.of("inventoryItemId", beef, "quantity", 0.1)).toList()))
                .andExpect(status().isBadRequest());

        recipe(dish, Map.of(beef, 0.1, herbs, 0.05)).andExpect(status().isOk());
        recipe(dish, Map.of(herbs, 0.02)).andExpect(status().isOk());
        ResultActions recipes = get("/api/recipes", as("quanly"));
        String lines = "$[?(@.menuItemId == " + dish + ")].lines[*]";
        List<Number> ingredients = read(recipes, lines + ".inventoryItemId");
        List<Number> amounts = read(recipes, lines + ".quantity");
        assertThat(ingredients).extracting(Number::longValue).containsExactly(herbs);
        assertThat(amounts).extracting(Number::doubleValue).containsExactly(0.02);

        // No lines takes the recipe away, and the dish then takes nothing.
        put("/api/menu-items/" + dish + "/recipe", as("quanly"), Map.of("lines", List.of()))
                .andExpect(status().isOk());
        List<Object> left = read(get("/api/recipes", as("quanly")), "$[?(@.menuItemId == " + dish + ")]");
        assertThat(left).isEmpty();
        addDish(openOrder(newTable().id()), dish, 1);
        assertThat(stock(herbs)).isEqualTo(10.0);

        // A dish never ordered can still be deleted, and its recipe goes with it.
        long unused = newDish(10_000);
        recipe(unused, Map.of(beef, 0.1)).andExpect(status().isOk());
        delete("/api/menu-items/" + unused, as("quanly")).andExpect(status().is2xxSuccessful());
    }

    @Test
    void usageAddsUpWhatDishesTookNetOfWhatCameBack() throws Exception {
        long pork = newItem(20);
        long dish = newDish(55_000);
        recipe(dish, Map.of(pork, 0.2)).andExpect(status().isOk());
        long orderId = openOrder(newTable().id());
        long cancelled = addDish(orderId, dish, 2);
        post("/api/order-items/" + cancelled + "/cancel", as("phucvu"), Map.of()).andExpect(status().isOk());
        addDish(orderId, dish, 3);
        String movements = "/api/inventory-items/" + pork + "/movements";
        post(movements, as("quanly"), Map.of("type", "OUT", "quantity", 1)).andExpect(status().isCreated());
        post(movements, as("quanly"), Map.of("type", "ADJUST", "quantity", 18)).andExpect(status().isCreated());

        // US-33 AC6: 20 − 0,6 − 1 = 18,4 on the books; the count found 18.
        String usage = "/api/inventory-usage?from=" + TODAY + "&to=" + TODAY;
        get(usage, as("quanly"))
                .andExpect(jsonPath("$[?(@.inventoryItemId == %d)].used", pork).value(hasItem(0.6)))
                .andExpect(jsonPath("$[?(@.inventoryItemId == %d)].removed", pork).value(hasItem(1.0)))
                .andExpect(jsonPath("$[?(@.inventoryItemId == %d)].adjusted", pork).value(hasItem(-0.4)));
        get("/api/inventory-usage?from=2026-01-01&to=2026-06-30", as("quanly")).andExpect(status().isBadRequest());
    }

    @Test
    void onlyManagersSeeRecipesAndUsage() throws Exception {
        long dish = newDish(10_000);
        String usage = "/api/inventory-usage?from=" + TODAY + "&to=" + TODAY;

        // US-33 AC7
        for (String user : List.of("phucvu", "bep", "thungan")) {
            get("/api/recipes", as(user)).andExpect(status().isForbidden());
            put("/api/menu-items/" + dish + "/recipe", as(user), Map.of("lines", List.of()))
                    .andExpect(status().isForbidden());
            get(usage, as(user)).andExpect(status().isForbidden());
        }
        get("/api/recipes", as("admin")).andExpect(status().isOk());
        get(usage, as("admin")).andExpect(status().isOk());
    }

    /** A new ingredient in kg with this much in stock. */
    private long newItem(double stock) throws Exception {
        long id = readLong(post("/api/inventory-items", as("quanly"),
                Map.of("name", unique("Nguyên liệu"), "unit", "kg", "minQuantity", 0))
                .andExpect(status().isCreated()), "$.id");
        post("/api/inventory-items/" + id + "/movements", as("quanly"), Map.of("type", "IN", "quantity", stock))
                .andExpect(status().isCreated());
        return id;
    }

    private ResultActions recipe(long dishId, Map<Long, ? extends Number> lines) throws Exception {
        return put("/api/menu-items/" + dishId + "/recipe", as("quanly"), Map.of("lines", lines.entrySet().stream()
                .map(e -> Map.of("inventoryItemId", e.getKey(), "quantity", e.getValue())).toList()));
    }

    private double stock(long itemId) throws Exception {
        List<Number> values = read(get("/api/inventory-items", as("quanly")), "$[?(@.id == " + itemId + ")].quantity");
        return values.getFirst().doubleValue();
    }
}
