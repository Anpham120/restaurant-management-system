package com.restaurant.core.inventory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.restaurant.core.order.OrderApiTestBase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Danh mục nguyên liệu, nhập → xuất → bếp nhận → sơ chế → hủy/hoàn trả/kiểm kê, lô hỏng, báo cáo kho. */
class InventoryIT extends OrderApiTestBase {

    private static String uniq(String p) {
        return p + " " + System.nanoTime() % 1_000_000_000L;
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private ResultActions putJson(String token, String url, String body, Object... vars) throws Exception {
        return mvc.perform(put(url, vars).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long ingredient(String name) throws Exception {
        return readLong(postJson(manager, "/api/ingredients", "{\"name\":\"" + name + "\",\"baseUnit\":\"g\",\"minQuantity\":1000,\"expiryTracked\":true}")
                .andExpect(status().isCreated()), "$.data.id");
    }

    private long type(long ingredientId, String name, String handling) throws Exception {
        return readLong(postJson(manager, "/api/kitchen-ingredient-types",
                "{\"rawIngredientId\":" + ingredientId + ",\"name\":\"" + name + "\",\"handlingType\":\"" + handling + "\",\"shelfLifeHours\":48}")
                .andExpect(status().isCreated()), "$.data.id");
    }

    private String receiptBody(String requestKey, long ingredientId, int kg) {
        return "{\"requestKey\":\"" + requestKey + "\",\"supplierName\":\"NCC Test\",\"items\":[{\"ingredientId\":" + ingredientId
                + ",\"inputQuantity\":" + kg + ",\"inputUnit\":\"kg\",\"conversionFactor\":1000,\"unitPricePerInputUnit\":120000,"
                + "\"lotCode\":\"L1\",\"expiryDate\":\"" + LocalDate.now().plusDays(30) + "\"}]}";
    }

    /** Nhập + xác nhận một lô RAW; trả id lô. */
    private long receive(long ingredientId, int kg) throws Exception {
        long receipt = readLong(postJson(manager, "/api/stock-receipts", receiptBody(key(), ingredientId, kg)).andExpect(status().isCreated()), "$.data.id");
        return readLong(postJson(manager, "/api/stock-receipts/{id}/confirm", "{}", receipt).andExpect(status().isOk()), "$.data.lines[0].inventoryBatchId");
    }

    private java.math.BigDecimal rawOnHand(long batchId) {
        return new java.math.BigDecimal(data.value("SELECT quantity_on_hand FROM inventory_batches WHERE id = ?", batchId));
    }

    @Test
    void catalog() throws Exception {
        String name = uniq("Thịt bò");
        long beef = ingredient(name);
        postJson(manager, "/api/ingredients", "{\"name\":\"" + name + "\",\"baseUnit\":\"g\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("DUPLICATE"));
        postJson(kitchen, "/api/ingredients", "{\"name\":\"x\",\"baseUnit\":\"g\"}").andExpect(status().isForbidden());
        getAs(waiter, "/api/ingredients").andExpect(status().isForbidden());
        getAs(kitchen, "/api/ingredients/{id}", beef).andExpect(status().isOk()).andExpect(jsonPath("$.data.expiryTracked").value(true));

        long direct = type(beef, uniq("Bò nguyên miếng"), "DIRECT");
        postJson(manager, "/api/kitchen-ingredient-types", "{\"rawIngredientId\":" + beef + ",\"name\":\"" + uniq("Bò khác") + "\",\"handlingType\":\"DIRECT\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("DUPLICATE"));
        long sliced = type(beef, uniq("Bò thái lát"), "PREPARED");
        getAs(kitchen, "/api/kitchen-ingredient-types?ingredientId=" + beef).andExpect(jsonPath("$.data.length()").value(2));
        putJson(manager, "/api/kitchen-ingredient-types/{id}", "{\"rawIngredientId\":" + beef + ",\"name\":\"x\",\"handlingType\":\"PREPARED\"}", direct)
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.error.code").value("IMMUTABLE_FIELD"));
        putJson(manager, "/api/kitchen-ingredient-types/{id}", "{\"rawIngredientId\":" + beef + ",\"name\":\"Bò lát mỏng\",\"handlingType\":\"PREPARED\",\"active\":false}", sliced)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.active").value(false));
        putJson(manager, "/api/ingredients/{id}", "{\"name\":\"" + name + "\",\"baseUnit\":\"g\",\"minQuantity\":5000}", beef)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.minQuantity").value(5000));
    }

    @Test
    void receiptIdempotencyConfirmAndDelete() throws Exception {
        long rice = ingredient(uniq("Gạo"));
        String k = key();
        long id = readLong(postJson(manager, "/api/stock-receipts", receiptBody(k, rice, 10)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT")).andExpect(jsonPath("$.data.totalAmount").value(1200000.0))
                .andExpect(jsonPath("$.data.lines[0].baseQuantity").value(10000.0)), "$.data.id");
        assertThat(readLong(postJson(manager, "/api/stock-receipts", receiptBody(k, rice, 10)), "$.data.id")).isEqualTo(id);
        postJson(manager, "/api/stock-receipts", receiptBody(k, rice, 11))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("RC409"));
        postJson(kitchen, "/api/stock-receipts", receiptBody(key(), rice, 1)).andExpect(status().isForbidden());

        postJson(manager, "/api/stock-receipts/{id}/confirm", "{}", id).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.lines[0].inventoryBatchId", notNullValue()));
        postJson(manager, "/api/stock-receipts/{id}/confirm", "{}", id).andExpect(status().isConflict());
        mvc.perform(delete("/api/stock-receipts/{id}", id).header("Authorization", manager))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("NOT_DRAFT"));

        long draft = readLong(postJson(manager, "/api/stock-receipts", receiptBody(key(), rice, 2)), "$.data.id");
        mvc.perform(delete("/api/stock-receipts/{id}", draft).header("Authorization", manager)).andExpect(status().isOk());
        getAs(manager, "/api/stock-receipts/{id}", draft).andExpect(status().isNotFound());
        getAs(manager, "/api/stock-receipts?status=CONFIRMED&size=5").andExpect(jsonPath("$.data[*].id", hasItem((int) id)));

        getAs(manager, "/api/inventory/raw-stock").andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.ingredientId == " + rice + ")].usableQuantity").value(hasItem(10000.0)));
        getAs(manager, "/api/inventory/raw-lots?ingredientId=" + rice).andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void issueReceivePrepareAdjustReturnCount() throws Exception {
        long fish = ingredient(uniq("Cá hồi"));
        long whole = type(fish, uniq("Cá hồi phi lê"), "DIRECT");
        long sliced = type(fish, uniq("Cá hồi cắt sashimi"), "PREPARED");
        long batch = receive(fish, 10);                                       // 10 000 g

        String issueBody = "{\"requestKey\":\"" + key() + "\",\"note\":\"ca sáng\",\"items\":["
                + "{\"inventoryBatchId\":" + batch + ",\"quantity\":2000,\"fulfillmentMode\":\"DIRECT\"},"
                + "{\"inventoryBatchId\":" + batch + ",\"quantity\":3000,\"fulfillmentMode\":\"PREPARE\"}]}";
        ResultActions created = postJson(manager, "/api/stock-issues", issueBody).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
        long issue = readLong(created, "$.data.id");
        List<Number> directLine = read(created, "$.data.lines[?(@.fulfillmentMode == 'DIRECT')].id");
        List<Number> prepLine = read(created, "$.data.lines[?(@.fulfillmentMode == 'PREPARE')].id");

        postEmpty(kitchen, "/api/stock-issues/{id}/receive", issue).andExpect(status().isConflict());   // chưa xuất
        postEmpty(manager, "/api/stock-issues/{id}/issue", issue).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ISSUED"));
        assertThat(rawOnHand(batch).intValue()).isEqualTo(5000);
        getAs(kitchen, "/api/inventory/handover").andExpect(jsonPath("$.data[*].stockIssueId", hasItem((int) issue)));
        ResultActions received = postEmpty(kitchen, "/api/stock-issues/{id}/receive", issue).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RECEIVED"));
        List<Number> kitchenBatches = read(received, "$.data.lines[?(@.fulfillmentMode == 'DIRECT')].kitchenBatchId");
        long directBatch = kitchenBatches.get(0).longValue();
        postJson(manager, "/api/stock-issues/{id}/cancel", reason("nhầm"), issue).andExpect(status().isConflict());
        getAs(kitchen, "/api/inventory/kitchen-lots?typeId=" + whole).andExpect(jsonPath("$.data[0].quantityOnHand").value(2000.0));

        // sơ chế 3000 g WIP → 2500 g cá cắt, hao hụt 500
        String prepBody = "{\"requestKey\":\"" + key() + "\",\"stockIssueItemId\":" + prepLine.get(0) + ",\"inputQuantity\":3000,"
                + "\"lossReason\":\"lọc xương\",\"outputs\":[{\"kitchenIngredientTypeId\":" + sliced + ",\"outputQuantity\":2500}]}";
        long prep = readLong(postJson(kitchen, "/api/kitchen-preparations", prepBody).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.lossQuantity").value(500.0)), "$.data.id");
        assertThat(readLong(postJson(kitchen, "/api/kitchen-preparations", prepBody), "$.data.id")).isEqualTo(prep);
        postEmpty(kitchen, "/api/kitchen-preparations/{id}/confirm", prep).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.outputs[0].kitchenBatchId", notNullValue()))
                .andExpect(jsonPath("$.data.outputs[0].usableUntil", notNullValue()));
        postEmpty(kitchen, "/api/kitchen-preparations/{id}/confirm", prep).andExpect(status().isConflict());
        LocalDate today = LocalDate.now();
        getAs(manager, "/api/inventory/preparation-yield?from=" + today.minusDays(1) + "&to=" + today.plusDays(1))
                .andExpect(jsonPath("$.data[?(@.preparationId == " + prep + ")].lossQuantity").value(hasItem(500.0)));

        // hủy 100 g lô bếp DIRECT: bếp lập, chỉ Manager xác nhận
        long adj = readLong(postJson(kitchen, "/api/stock-adjustments", "{\"requestKey\":\"" + key() + "\",\"adjustmentType\":\"DAMAGE\","
                + "\"reason\":\"rơi vỡ\",\"items\":[{\"kitchenBatchId\":" + directBatch + ",\"quantity\":100}]}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.lines[0].quantityDelta").value(-100.0)), "$.data.id");
        postEmpty(kitchen, "/api/stock-adjustments/{id}/confirm", adj).andExpect(status().isForbidden());
        postEmpty(manager, "/api/stock-adjustments/{id}/confirm", adj).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CONFIRMED"));
        postJson(kitchen, "/api/stock-adjustments", "{\"requestKey\":\"" + key() + "\",\"adjustmentType\":\"DAMAGE\",\"reason\":\"x\","
                + "\"items\":[{\"rawBatchId\":" + batch + ",\"kitchenBatchId\":" + directBatch + ",\"quantity\":1}]}")
                .andExpect(status().isBadRequest());

        // trả 400 g DIRECT về kho RAW
        long ret = readLong(postJson(kitchen, "/api/stock-returns", "{\"requestKey\":\"" + key() + "\",\"reason\":\"dư\",\"items\":[{"
                + "\"sourceIssueItemId\":" + directLine.get(0) + ",\"directKitchenBatchId\":" + directBatch + ",\"quantity\":400}]}")
                .andExpect(status().isCreated()), "$.data.id");
        postEmpty(manager, "/api/stock-returns/{id}/confirm", ret).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CONFIRMED"));
        assertThat(rawOnHand(batch).intValue()).isEqualTo(5400);
        getAs(kitchen, "/api/inventory/kitchen-lots?typeId=" + whole).andExpect(jsonPath("$.data[0].quantityOnHand").value(1500.0));

        // kiểm kê: thực tế thiếu 50 g
        long count = readLong(postJson(manager, "/api/stock-counts", "{\"requestKey\":\"" + key() + "\",\"items\":[{\"rawBatchId\":" + batch
                + ",\"actualQuantity\":5350,\"reason\":\"hao hụt tự nhiên\"}]}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.lines[0].bookQuantity").value(5400.0))
                .andExpect(jsonPath("$.data.lines[0].difference").value(-50.0)), "$.data.id");
        postEmpty(manager, "/api/stock-counts/{id}/confirm", count).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CONFIRMED"));
        assertThat(rawOnHand(batch).intValue()).isEqualTo(5350);

        // danh sách phiếu (mới nhất trước) cho màn hình duyệt nháp
        getAs(kitchen, "/api/kitchen-preparations?status=CONFIRMED&size=5").andExpect(jsonPath("$.data[*].id", hasItem((int) prep)));
        getAs(kitchen, "/api/stock-adjustments").andExpect(jsonPath("$.data[*].id", hasItem((int) adj)));
        getAs(manager, "/api/stock-returns?status=CONFIRMED").andExpect(jsonPath("$.data[*].id", hasItem((int) ret)));
        getAs(manager, "/api/stock-counts").andExpect(jsonPath("$.data[*].id", hasItem((int) count)));
        getAs(kitchen, "/api/stock-counts").andExpect(status().isForbidden());
        getAs(kitchen, "/api/inventory/kitchen-lots?typeId=" + whole).andExpect(jsonPath("$.data[0].directIssueItemId").value(directLine.get(0).intValue()));

        Instant now = Instant.now();
        getAs(manager, "/api/inventory/history?from=" + now.minus(1, ChronoUnit.HOURS) + "&to=" + now.plus(1, ChronoUnit.HOURS) + "&ingredientId=" + fish)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(6)));
    }

    @Test
    void cancelIssueAndMarkUnusable() throws Exception {
        long herb = ingredient(uniq("Rau thơm"));
        type(herb, uniq("Rau thơm nhặt"), "DIRECT");
        long batch = receive(herb, 2);
        long issue = readLong(postJson(manager, "/api/stock-issues", "{\"requestKey\":\"" + key() + "\",\"items\":[{\"inventoryBatchId\":" + batch
                + ",\"quantity\":500,\"fulfillmentMode\":\"DIRECT\"}]}"), "$.data.id");
        postEmpty(manager, "/api/stock-issues/{id}/issue", issue).andExpect(status().isOk());
        assertThat(rawOnHand(batch).intValue()).isEqualTo(1500);
        postJson(manager, "/api/stock-issues/{id}/cancel", "{\"reason\":\" \"}", issue).andExpect(status().isBadRequest());
        postJson(manager, "/api/stock-issues/{id}/cancel", reason("bếp không cần"), issue).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        assertThat(rawOnHand(batch).intValue()).isEqualTo(2000);

        postJson(kitchen, "/api/stock-batches/RAW/{id}/mark-unusable", reason("mốc"), batch).andExpect(status().isForbidden());
        postJson(manager, "/api/stock-batches/BAD/{id}/mark-unusable", reason("mốc"), batch).andExpect(status().isBadRequest());
        postJson(manager, "/api/stock-batches/RAW/{id}/mark-unusable", reason("mốc"), batch).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.affectedOrderItems").value(0));
        getAs(kitchen, "/api/inventory/unusable-lots")
                .andExpect(jsonPath("$.data[?(@.location == 'RAW' && @.batchId == " + batch + ")].reason").value(hasItem("mốc")));
        getAs(manager, "/api/inventory/raw-lots?ingredientId=" + herb).andExpect(jsonPath("$.data[0].usable").value(false));
    }
}
