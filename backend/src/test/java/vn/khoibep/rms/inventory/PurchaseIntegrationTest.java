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

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import vn.khoibep.rms.IntegrationTest;

/** P2-03: suppliers, goods receipts with prices and the unit cost of ingredients (FR-09.5 → FR-09.7, US-32, BR-37). */
class PurchaseIntegrationTest extends IntegrationTest {

    private static final String TODAY = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).toString();

    @Test
    void aReceiptBringsStockInAndAveragesTheCost() throws Exception {
        long supplier = newSupplier();
        long pork = newItem("kg");

        // A first receipt: no cost yet, so the price is the cost.
        receive(supplier, pork, 12, 110_000).andExpect(status().isCreated());
        get("/api/inventory-items", as("quanly"))
                .andExpect(jsonPath("$[?(@.id == %d)].unitCost", pork).value(hasItem(110_000)));

        // US-32 AC1 and AC2: (12 × 110.000 + 10 × 120.000) ÷ 22 = 114.545.
        ResultActions receipt = receive(supplier, pork, 10, 120_000)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(1_200_000))
                .andExpect(jsonPath("$.lines[0].lineTotal").value(1_200_000))
                .andExpect(jsonPath("$.createdByName").value("Nguyễn Văn Quản"));
        long receiptId = readLong(receipt, "$.id");
        get("/api/inventory-items", as("quanly"))
                .andExpect(jsonPath("$[?(@.id == %d)].quantity", pork).value(hasItem(22.0)))
                .andExpect(jsonPath("$[?(@.id == %d)].unitCost", pork).value(hasItem(114_545)));
        get("/api/inventory-items/" + pork + "/movements", as("quanly"))
                .andExpect(jsonPath("$[0].type").value("IN"))
                .andExpect(jsonPath("$[0].quantityChange").value(10.0))
                .andExpect(jsonPath("$[0].note").value(startsWith("Phiếu nhập #" + receiptId)));
        get("/api/goods-receipts?from=" + TODAY + "&to=" + TODAY, as("quanly"))
                .andExpect(jsonPath("$[?(@.id == %d)].total", receiptId).value(hasItem(1_200_000)));
        get("/api/goods-receipts/" + receiptId, as("quanly")).andExpect(jsonPath("$.lines.length()").value(1));
    }

    @Test
    void movementsByHandLeaveTheCostAlone() throws Exception {
        long pork = newItem("kg");
        receive(newSupplier(), pork, 10, 100_000).andExpect(status().isCreated());
        String movements = "/api/inventory-items/" + pork + "/movements";

        // US-32 AC4
        post(movements, as("quanly"), Map.of("type", "IN", "quantity", 5)).andExpect(status().isCreated());
        post(movements, as("quanly"), Map.of("type", "OUT", "quantity", 3)).andExpect(status().isCreated());
        post(movements, as("quanly"), Map.of("type", "ADJUST", "quantity", 9)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.unitCost").value(100_000));
    }

    @Test
    void aReceiptNeedsAnActiveSupplierAndSensibleLines() throws Exception {
        String name = unique("Nhà cung cấp");
        long supplier = newSupplier(name);
        long item = newItem("kg");

        // US-32 AC3
        post("/api/goods-receipts", as("quanly"), Map.of("supplierId", supplier, "lines", List.of()))
                .andExpect(status().isBadRequest());
        receive(supplier, item, 0, 10_000).andExpect(status().isBadRequest());
        receive(supplier, item, 1, -1).andExpect(status().isBadRequest());
        put("/api/suppliers/" + supplier, as("quanly"), Map.of("name", name, "active", false))
                .andExpect(jsonPath("$.active").value(false));
        ResultActions refused = receive(supplier, item, 1, 10_000).andExpect(status().isConflict());
        assertThat(body(refused)).contains("ngừng giao dịch");
        get("/api/inventory-items", as("quanly"))
                .andExpect(jsonPath("$[?(@.id == %d)].quantity", item).value(hasItem(0.0)));
    }

    @Test
    void aSavedReceiptCannotBeChanged() throws Exception {
        long receiptId = readLong(receive(newSupplier(), newItem("kg"), 2, 50_000), "$.id");

        // US-32 AC4: there is no edit and no delete.
        mvc.perform(MockMvcRequestBuilders.put("/api/goods-receipts/" + receiptId)
                        .header("Authorization", "Bearer " + as("quanly")))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(MockMvcRequestBuilders.delete("/api/goods-receipts/" + receiptId)
                        .header("Authorization", "Bearer " + as("quanly")))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void supplierNamesAreUniqueAndOnlyManagersBuy() throws Exception {
        String name = unique("Chợ đầu mối");
        post("/api/suppliers", as("quanly"), Map.of("name", name)).andExpect(status().isCreated());
        post("/api/suppliers", as("quanly"), Map.of("name", name.toUpperCase())).andExpect(status().isConflict());

        // US-32 AC5
        for (String user : List.of("phucvu", "bep", "thungan")) {
            get("/api/suppliers", as(user)).andExpect(status().isForbidden());
            get("/api/goods-receipts?from=" + TODAY + "&to=" + TODAY, as(user)).andExpect(status().isForbidden());
        }
        get("/api/suppliers", as("admin")).andExpect(status().isOk());
        get("/api/goods-receipts?from=2026-01-01&to=2026-06-30", as("quanly")).andExpect(status().isBadRequest());
    }

    private long newSupplier() throws Exception {
        return newSupplier(unique("Nhà cung cấp"));
    }

    private long newSupplier(String name) throws Exception {
        return readLong(post("/api/suppliers", as("quanly"), Map.of("name", name, "phone", "0900000001"))
                .andExpect(status().isCreated()), "$.id");
    }

    private long newItem(String unit) throws Exception {
        return readLong(post("/api/inventory-items", as("quanly"),
                Map.of("name", unique("Nguyên liệu"), "unit", unit, "minQuantity", 1))
                .andExpect(status().isCreated()), "$.id");
    }

    private ResultActions receive(long supplierId, long itemId, double quantity, long unitPrice) throws Exception {
        return post("/api/goods-receipts", as("quanly"), Map.of("supplierId", supplierId, "lines",
                List.of(Map.of("inventoryItemId", itemId, "quantity", quantity, "unitPrice", unitPrice))));
    }
}
