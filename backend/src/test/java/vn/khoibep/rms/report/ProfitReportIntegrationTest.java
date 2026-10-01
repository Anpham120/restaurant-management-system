package vn.khoibep.rms.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import vn.khoibep.rms.IntegrationTest;

/** P2-04: gross profit by dish and the exceptions report (FR-10.4, FR-10.5, US-35, BR-40). */
class ProfitReportIntegrationTest extends IntegrationTest {

    private static final String TODAY = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).toString();

    @Test
    void grossProfitUsesTheCostOfTheMomentTheDishWentToTheKitchen() throws Exception {
        long supplier = newSupplier();
        long beef = newItem();
        receive(supplier, beef, 10, 200_000);
        String name = unique("Phở bò");
        long pho = newDish(name, 65_000);
        recipe(pho, beef, 0.15);
        long orderId = openOrder(newTable().id());
        addDish(orderId, pho, 2);
        payCash(orderId, 130_000);

        // US-35 AC1: 2 × 65.000 = 130.000; 2 × 0,15 kg × 200.000 = 60.000.
        expectDish(profit(), name, 2, 130_000, 60_000, 70_000);

        // US-35 AC2: a dearer receipt afterwards leaves the dishes already sold alone.
        receive(supplier, beef, 10, 300_000);
        expectDish(profit(), name, 2, 130_000, 60_000, 70_000);
    }

    @Test
    void dishesWithoutAFullCostShowNoneAndOnlyPaidDishesCount() throws Exception {
        String tea = unique("Trà đá");
        long teaId = newDish(tea, 5_000);
        long herbs = newItem();
        post("/api/inventory-items/" + herbs + "/movements", as("quanly"), Map.of("type", "IN", "quantity", 5))
                .andExpect(status().isCreated());
        String salad = unique("Nộm");
        long saladId = newDish(salad, 40_000);
        recipe(saladId, herbs, 0.1);
        long orderId = openOrder(newTable().id());
        addDish(orderId, teaId, 2);
        addDish(orderId, saladId, 1);
        long cancelled = addDish(orderId, saladId, 3);
        post("/api/order-items/" + cancelled + "/cancel", as("phucvu"), Map.of()).andExpect(status().isOk());
        payCash(orderId, 50_000);
        addDish(openOrder(newTable().id()), teaId, 4);

        // US-35 AC3: no recipe, or an ingredient with no cost yet: no cost and no profit. Cancelled and unpaid dishes
        // are left out.
        ResultActions profit = profit();
        expectDish(profit, tea, 2, 10_000, null, null);
        expectDish(profit, salad, 1, 40_000, null, null);
    }

    @Test
    void discountsAreWhatTheDishesLostOnTheBill() throws Exception {
        ResultActions before = profit();
        long orderId = openOrder(newTable().id());
        addDish(orderId, newDish(100_000), 1);
        post("/api/orders/" + orderId + "/adjustments", as("thungan"),
                Map.of("type", "DISCOUNT", "amount", 10_000, "reason", "WAIT"))
                .andExpect(status().isCreated());
        payCash(orderId, 90_000);

        ResultActions after = profit();
        assertThat(readLong(after, "$.dishRevenue") - readLong(before, "$.dishRevenue")).isEqualTo(100_000);
        assertThat(readLong(after, "$.revenue") - readLong(before, "$.revenue")).isEqualTo(90_000);
        assertThat(readLong(after, "$.discounts") - readLong(before, "$.discounts")).isEqualTo(10_000);
    }

    @Test
    void exceptionsCountWhoCancelledDishes() throws Exception {
        EmployeeRef waiter = newEmployee("WAITER", "HOURLY", 25_000);
        long orderId = openOrder(newTable().id());
        long dish = newDish(30_000);
        long first = addDish(orderId, dish, 1);
        long second = addDish(orderId, dish, 2);
        for (long item : List.of(first, second)) {
            post("/api/order-items/" + item + "/cancel", as(waiter.username()), Map.of()).andExpect(status().isOk());
        }
        // A price change is in the audit log but is no exception.
        String name = unique("Món đổi giá");
        long priced = newDish(name, 45_000);
        long categoryId = readLong(get("/api/categories", as("quanly")), "$[0].id");
        put("/api/menu-items/" + priced, as("quanly"), Map.of("categoryId", categoryId, "name", name, "price", 50_000))
                .andExpect(status().isOk());

        // US-35 AC4
        String person = "$.byPerson[?(@.employeeId == %d && @.action == 'ITEM_CANCELLED')]";
        exceptions()
                .andExpect(jsonPath(person + ".count", waiter.id()).value(hasItem(2)))
                .andExpect(jsonPath(person + ".amount", waiter.id()).value(hasItem(90_000)))
                .andExpect(jsonPath("$.byAction[?(@.action == 'PRICE_CHANGED')]").isEmpty());
    }

    @Test
    void onlyManagersReadProfitAndExceptions() throws Exception {
        // US-35 AC6
        for (String user : List.of("phucvu", "bep", "thungan")) {
            get("/api/reports/gross-profit?from=" + TODAY + "&to=" + TODAY, as(user)).andExpect(status().isForbidden());
            get("/api/reports/exceptions?from=" + TODAY + "&to=" + TODAY, as(user)).andExpect(status().isForbidden());
        }
        get("/api/reports/gross-profit?from=" + TODAY + "&to=" + TODAY, as("admin")).andExpect(status().isOk());
        get("/api/reports/exceptions?from=2024-01-01&to=2026-01-01", as("quanly")).andExpect(status().isBadRequest());
    }

    private ResultActions profit() throws Exception {
        return get("/api/reports/gross-profit?from=" + TODAY + "&to=" + TODAY, as("quanly")).andExpect(status().isOk());
    }

    private ResultActions exceptions() throws Exception {
        return get("/api/reports/exceptions?from=" + TODAY + "&to=" + TODAY, as("quanly")).andExpect(status().isOk());
    }

    private static void expectDish(ResultActions report, String name, int quantity, long revenue, Integer cost,
                                   Integer grossProfit) throws Exception {
        String dish = "$.dishes[?(@.itemName == '" + name + "')]";
        report.andExpect(jsonPath(dish + ".quantity").value(hasItem(quantity)))
                .andExpect(jsonPath(dish + ".revenue").value(hasItem((int) revenue)))
                .andExpect(jsonPath(dish + ".cost").value(cost == null ? hasItem(nullValue()) : hasItem(cost)))
                .andExpect(jsonPath(dish + ".grossProfit").value(grossProfit == null ? hasItem(nullValue())
                        : hasItem(grossProfit)));
    }

    private void payCash(long orderId, long amount) throws Exception {
        post("/api/orders/" + orderId + "/payments/cash", as("thungan"), Map.of("receivedAmount", amount))
                .andExpect(status().isOk());
    }

    private void recipe(long dishId, long itemId, double quantity) throws Exception {
        put("/api/menu-items/" + dishId + "/recipe", as("quanly"),
                Map.of("lines", List.of(Map.of("inventoryItemId", itemId, "quantity", quantity))))
                .andExpect(status().isOk());
    }

    private long newSupplier() throws Exception {
        return readLong(post("/api/suppliers", as("quanly"), Map.of("name", unique("Chợ")))
                .andExpect(status().isCreated()), "$.id");
    }

    private long newItem() throws Exception {
        return readLong(post("/api/inventory-items", as("quanly"),
                Map.of("name", unique("Nguyên liệu"), "unit", "kg", "minQuantity", 0))
                .andExpect(status().isCreated()), "$.id");
    }

    private void receive(long supplierId, long itemId, double quantity, long unitPrice) throws Exception {
        post("/api/goods-receipts", as("quanly"), Map.of("supplierId", supplierId, "lines",
                List.of(Map.of("inventoryItemId", itemId, "quantity", quantity, "unitPrice", unitPrice))))
                .andExpect(status().isCreated());
    }
}
