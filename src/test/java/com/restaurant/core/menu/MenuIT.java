package com.restaurant.core.menu;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.restaurant.core.order.OrderApiTestBase;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Danh mục, món, công thức (version), thực đơn ngày, hạn mức, tạm dừng — và hợp đồng "thực đơn hôm nay" với Order. */
class MenuIT extends OrderApiTestBase {

    private static String uniq(String p) {
        return p + " " + System.nanoTime() % 1_000_000_000L;
    }

    private long createCategory(String name) throws Exception {
        return readLong(postJson(manager, "/api/menu-categories", "{\"name\":\"" + name + "\",\"sortOrder\":1}")
                .andExpect(status().isCreated()), "$.data.id");
    }

    private long createItem(long categoryId, String name, int price) throws Exception {
        return readLong(postJson(manager, "/api/menu-items", "{\"categoryId\":" + categoryId + ",\"name\":\"" + name + "\",\"price\":" + price + "}")
                .andExpect(status().isCreated()), "$.data.id");
    }

    private ResultActions putJson(String token, String url, String body, Object... vars) throws Exception {
        return mvc.perform(put(url, vars).header("Authorization", token).contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void categoriesAndItems() throws Exception {
        String name = uniq("Món nướng");
        long cat = createCategory(name);
        postJson(manager, "/api/menu-categories", "{\"name\":\"" + name.toUpperCase() + "\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("DUPLICATE"));
        postJson(waiter, "/api/menu-categories", "{\"name\":\"x\"}").andExpect(status().isForbidden());

        String item = uniq("Sườn nướng");
        long id = createItem(cat, item, 45000);
        getAs(waiter, "/api/menu-items/{id}", id).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.price").value(45000.0))
                .andExpect(jsonPath("$.data.categoryName").value(name))
                .andExpect(jsonPath("$.data.currentRecipeVersion").doesNotExist());
        putJson(manager, "/api/menu-items/{id}", "{\"categoryId\":" + cat + ",\"name\":\"" + item + "\",\"price\":50000,\"active\":false}", id)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.price").value(50000.0)).andExpect(jsonPath("$.data.active").value(false));
        getAs(waiter, "/api/menu-items?categoryId=" + cat).andExpect(jsonPath("$.data", hasSize(0)));
        getAs(manager, "/api/menu-items?categoryId=" + cat + "&activeOnly=false").andExpect(jsonPath("$.data", hasSize(1)));
        postJson(manager, "/api/menu-items", "{\"categoryId\":" + cat + ",\"name\":\"Âm\",\"price\":-1}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details[0].field").value("price"));
        postJson(manager, "/api/menu-items", "{\"categoryId\":999999999,\"name\":\"" + uniq("Không nhóm") + "\",\"price\":1}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.error.code").value("REFERENCE_NOT_FOUND"));
        putJson(manager, "/api/menu-categories/{id}", "{\"name\":\"" + name + "\",\"sortOrder\":5,\"active\":false}", cat)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.sortOrder").value(5)).andExpect(jsonPath("$.data.active").value(false));
    }

    @Test
    void recipeVersions() throws Exception {
        long type = data.dish(1000, null, 5).kitchenTypeId();
        long item = createItem(createCategory(uniq("Nhóm CT")), uniq("Bò lúc lắc"), 60000);
        postJson(manager, "/api/menu-items/{id}/recipes", "{\"ingredients\":[{\"kitchenIngredientTypeId\":" + type + ",\"quantityPerServing\":0.2}]}", item)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.ingredients[0].quantityPerServing").value(0.2));
        postJson(manager, "/api/menu-items/{id}/recipes", "{\"ingredients\":[{\"kitchenIngredientTypeId\":" + type + ",\"quantityPerServing\":0.25}]}", item)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.version").value(2)).andExpect(jsonPath("$.data.current").value(true));
        getAs(kitchen, "/api/menu-items/{id}/recipes", item).andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].version").value(2)).andExpect(jsonPath("$.data[0].current").value(true))
                .andExpect(jsonPath("$.data[1].current").value(false));
        getAs(waiter, "/api/menu-items/{id}", item).andExpect(jsonPath("$.data.currentRecipeVersion").value(2));
        postJson(manager, "/api/menu-items/{id}/recipes", "{\"ingredients\":[{\"kitchenIngredientTypeId\":999999999,\"quantityPerServing\":1}]}", item)
                .andExpect(status().isUnprocessableContent());
        getAs(manager, "/api/menu-items/{id}/recipes", item).andExpect(jsonPath("$.data", hasSize(2)));   // lần lỗi rollback trọn
        postJson(manager, "/api/menu-items/{id}/recipes", "{\"ingredients\":[]}", 999999999L).andExpect(status().isNotFound());
        getAs(waiter, "/api/menu-items/{id}/recipes", item).andExpect(status().isForbidden());
    }

    @Test
    void dailyMenuLifecycle() throws Exception {
        LocalDate date = LocalDate.now().plusDays(1000 + ThreadLocalRandom.current().nextInt(100_000));
        long cat = createCategory(uniq("Nhóm TĐ"));
        long noRecipe = createItem(cat, uniq("Chưa công thức"), 10000);
        long ready = createItem(cat, uniq("Có công thức"), 20000);
        postJson(manager, "/api/menu-items/{id}/recipes", "{\"ingredients\":[]}", ready).andExpect(status().isCreated());

        long menu = readLong(postJson(manager, "/api/daily-menus", "{\"businessDate\":\"" + date + "\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("DRAFT")), "$.data.id");
        postJson(manager, "/api/daily-menus", "{\"businessDate\":\"" + date + "\"}").andExpect(status().isConflict());
        postEmpty(manager, "/api/daily-menus/{id}/publish", menu).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("RC409"));
        postJson(manager, "/api/daily-menus/{id}/items", "{\"menuItemId\":" + noRecipe + "}", menu)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("MENU_ITEM_NOT_READY"));
        postJson(manager, "/api/daily-menus/{id}/items", "{\"menuItemId\":" + ready + ",\"quotaTotal\":30}", menu)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].remaining").value(30));
        postJson(manager, "/api/daily-menus/{id}/items", "{\"menuItemId\":" + ready + "}", menu)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("DUPLICATE"));
        postEmpty(manager, "/api/daily-menus/{id}/publish", menu).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PUBLISHED"));
        getAs(waiter, "/api/daily-menus?date=" + date).andExpect(jsonPath("$.data.id").value(menu));
        postEmpty(manager, "/api/daily-menus/{id}/close", menu).andExpect(jsonPath("$.data.status").value("CLOSED"));
        postEmpty(manager, "/api/daily-menus/{id}/publish", menu).andExpect(status().isConflict());
        postJson(manager, "/api/daily-menus/{id}/items", "{\"menuItemId\":" + noRecipe + "}", menu)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("MENU_CLOSED"));
        postJson(waiter, "/api/daily-menus", "{\"businessDate\":\"" + date.plusDays(1) + "\"}").andExpect(status().isForbidden());
    }

    private long dailyMenuItemOf(long menuId, long menuItemId) throws Exception {
        List<Number> ids = read(getAs(manager, "/api/daily-menus/{id}", menuId), "$.data.items[?(@.menuItemId == " + menuItemId + ")].dailyMenuItemId");
        return ids.get(0).longValue();
    }

    private long createMenu(LocalDate date, boolean fromCatalog) throws Exception {
        return readLong(postJson(manager, "/api/daily-menus", "{\"businessDate\":\"" + date + "\",\"fromCatalog\":" + fromCatalog + "}")
                .andExpect(status().isCreated()), "$.data.id");
    }

    /** Thực đơn mặc định: tạo từ danh mục (hạn mức 0), nhập số suất hàng loạt, điền theo số đã bán của thực đơn khác. */
    @Test
    void dailyMenuFromCatalogAndQuotaFill() throws Exception {
        long type = data.dish(1000, 0, 10).kitchenTypeId();             // bảo đảm có thực đơn hôm nay + tồn bếp
        long cat = createCategory(uniq("Nhóm MĐ"));
        long ready = createItem(cat, uniq("Món sẵn"), 30000);
        postJson(manager, "/api/menu-items/{id}/recipes", "{\"ingredients\":[]}", ready).andExpect(status().isCreated());
        long noRecipe = createItem(cat, uniq("Món chưa CT"), 10000);
        String stoppedName = uniq("Món ngừng");
        long stopped = createItem(cat, stoppedName, 10000);
        postJson(manager, "/api/menu-items/{id}/recipes", "{\"ingredients\":[]}", stopped).andExpect(status().isCreated());
        putJson(manager, "/api/menu-items/{id}", "{\"categoryId\":" + cat + ",\"name\":\"" + stoppedName + "\",\"price\":10000,\"active\":false}", stopped)
                .andExpect(status().isOk());
        long sold = createItem(cat, uniq("Món đã bán"), 40000);
        postJson(manager, "/api/menu-items/{id}/recipes", "{\"ingredients\":[{\"kitchenIngredientTypeId\":" + type + ",\"quantityPerServing\":1}]}", sold)
                .andExpect(status().isCreated());

        // Hôm nay bán 2 suất "Món đã bán"
        ResultActions today = getAs(manager, "/api/daily-menus/today");
        long todayId = readLong(today, "$.data.id");
        String todayDate = read(today, "$.data.businessDate");
        postJson(manager, "/api/daily-menus/{id}/items", "{\"menuItemId\":" + sold + ",\"quotaTotal\":5}", todayId).andExpect(status().isCreated());
        long[] t = openTable();
        postEmpty(kitchen, "/api/order-items/{id}/accept", submitOne(t[2], dailyMenuItemOf(todayId, sold), 2))
                .andExpect(jsonPath("$.data.result").value("ACCEPTED"));

        LocalDate date = LocalDate.now().plusDays(1000 + ThreadLocalRandom.current().nextInt(100_000));
        long menu = readLong(postJson(manager, "/api/daily-menus", "{\"businessDate\":\"" + date + "\",\"fromCatalog\":true}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.items[?(@.menuItemId == " + ready + ")].quotaTotal").value(hasItem(0)))
                .andExpect(jsonPath("$.data.items[?(@.menuItemId == " + sold + ")].quotaTotal").value(hasItem(0)))
                .andExpect(jsonPath("$.data.items[?(@.menuItemId == " + noRecipe + ")]").isEmpty())
                .andExpect(jsonPath("$.data.items[?(@.menuItemId == " + stopped + ")]").isEmpty()), "$.data.id");

        long readyDmi = dailyMenuItemOf(menu, ready);
        putJson(manager, "/api/daily-menus/{id}/quotas", "{\"items\":[{\"dailyMenuItemId\":" + readyDmi + ",\"quotaTotal\":7}]}", menu)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[?(@.dailyMenuItemId == " + readyDmi + ")].quotaTotal").value(hasItem(7)));
        putJson(manager, "/api/daily-menus/{id}/quotas", "{\"items\":[{\"dailyMenuItemId\":" + readyDmi + ",\"quotaTotal\":9},"
                + "{\"dailyMenuItemId\":999999999,\"quotaTotal\":1}]}", menu).andExpect(status().isNotFound());
        getAs(manager, "/api/daily-menus/{id}", menu)                   // một dòng sai thì không đổi dòng nào
                .andExpect(jsonPath("$.data.items[?(@.dailyMenuItemId == " + readyDmi + ")].quotaTotal").value(hasItem(7)));
        putJson(waiter, "/api/daily-menus/{id}/quotas", "{\"items\":[{\"dailyMenuItemId\":" + readyDmi + ",\"quotaTotal\":1}]}", menu)
                .andExpect(status().isForbidden());

        // Điền theo hôm nay: món đã bán 2 suất → 2; món không có ở thực đơn hôm nay giữ nguyên
        postJson(manager, "/api/daily-menus/{id}/fill-quotas", "{\"fromDate\":\"" + todayDate + "\"}", menu)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.sourceDate").value(todayDate))
                .andExpect(jsonPath("$.data.menu.items[?(@.menuItemId == " + sold + ")].quotaTotal").value(hasItem(2)))
                .andExpect(jsonPath("$.data.menu.items[?(@.menuItemId == " + ready + ")].quotaTotal").value(hasItem(7)));

        // Không chỉ ngày: lấy thực đơn gần nhất trước đó (chính `date`, nơi món chưa bán suất nào)
        long next = createMenu(date.plusDays(1), true);
        long nextSoldDmi = dailyMenuItemOf(next, sold);
        putJson(manager, "/api/daily-menus/{id}/quotas", "{\"items\":[{\"dailyMenuItemId\":" + nextSoldDmi + ",\"quotaTotal\":5}]}", next)
                .andExpect(status().isOk());
        postEmpty(manager, "/api/daily-menus/{id}/fill-quotas", next)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.sourceDate").value(date.toString()))
                .andExpect(jsonPath("$.data.menu.items[?(@.menuItemId == " + sold + ")].quotaTotal").value(hasItem(0)));

        postEmpty(manager, "/api/daily-menus/{id}/publish", menu).andExpect(status().isOk());
        postEmpty(manager, "/api/daily-menus/{id}/fill-quotas", menu)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("MENU_NOT_DRAFT"));
    }

    /** Tạo thực đơn mới thì đóng thực đơn đã công bố của ngày đã qua (sau 04:00 chúng đã không bán được). */
    @Test
    void creatingMenuClosesPastPublishedMenus() throws Exception {
        data.dish(1000, null, null);                                    // bảo đảm thực đơn hôm nay đang bán
        long item = createItem(createCategory(uniq("Nhóm cũ")), uniq("Món cũ"), 20000);
        postJson(manager, "/api/menu-items/{id}/recipes", "{\"ingredients\":[]}", item).andExpect(status().isCreated());
        int offset = 1000 + ThreadLocalRandom.current().nextInt(100_000);
        long past = createMenu(LocalDate.now().minusDays(offset), false);
        postJson(manager, "/api/daily-menus/{id}/items", "{\"menuItemId\":" + item + "}", past).andExpect(status().isCreated());
        postEmpty(manager, "/api/daily-menus/{id}/publish", past).andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        createMenu(LocalDate.now().plusDays(offset), false);
        getAs(manager, "/api/daily-menus/{id}", past).andExpect(jsonPath("$.data.status").value("CLOSED"));
        getAs(manager, "/api/daily-menus/today").andExpect(jsonPath("$.data.status").value("PUBLISHED"));   // hôm nay không bị đóng
    }

    /** Tóm tắt nguyên liệu: dự kiến = hạn mức × định lượng, đã dùng = sổ COOK, tồn cuối ngày tính từ sổ kho. */
    @Test
    void ingredientUsageOfDailyMenu() throws Exception {
        long type = data.dish(1000, 0, 10).kitchenTypeId();             // món của TestData chưa mở bán → không tính dự kiến
        long unlimitedType = data.dish(1000, null, 4).kitchenTypeId();  // món không giới hạn suất → không có dự kiến
        long item = createItem(createCategory(uniq("Nhóm NL")), uniq("Gỏi"), 40000);
        postJson(manager, "/api/menu-items/{id}/recipes", "{\"ingredients\":[{\"kitchenIngredientTypeId\":" + type + ",\"quantityPerServing\":2}]}", item)
                .andExpect(status().isCreated());
        long todayId = readLong(getAs(manager, "/api/daily-menus/today"), "$.data.id");
        postJson(manager, "/api/daily-menus/{id}/items", "{\"menuItemId\":" + item + ",\"quotaTotal\":3}", todayId).andExpect(status().isCreated());

        long[] t = openTable();
        long orderItem = submitOne(t[2], dailyMenuItemOf(todayId, item), 1);
        postEmpty(kitchen, "/api/order-items/{id}/accept", orderItem).andExpect(jsonPath("$.data.result").value("ACCEPTED"));
        postEmpty(kitchen, "/api/order-items/{id}/start-cooking", orderItem).andExpect(status().isOk());

        ResultActions r = getAs(manager, "/api/daily-menus/{id}/ingredient-usage", todayId).andExpect(status().isOk());
        String row = "$.data[?(@.kitchenIngredientTypeId == " + type + ")]";
        assertEquals(0, dec(this.<List<Object>>read(r, row + ".planned").get(0)).compareTo(new BigDecimal("6")));
        assertEquals(0, dec(this.<List<Object>>read(r, row + ".used").get(0)).compareTo(new BigDecimal("2")));
        BigDecimal onHand = dec(this.<List<Object>>read(r, row + ".onHandAtEnd").get(0));
        assertEquals(0, onHand.compareTo(new BigDecimal("8")));
        assertEquals(0, onHand.compareTo(data.kitchenOnHand(type)));    // sổ kho khớp tồn thực tế
        r.andExpect(jsonPath(row + ".baseUnit").value(hasItem("phần")))
                .andExpect(jsonPath(row + ".hasUnlimited").value(hasItem(false)));

        String unlimited = "$.data[?(@.kitchenIngredientTypeId == " + unlimitedType + ")]";
        r.andExpect(jsonPath(unlimited + ".hasUnlimited").value(hasItem(true)))
                .andExpect(jsonPath(unlimited + ".planned").value(hasItem(nullValue())));
        assertEquals(0, dec(this.<List<Object>>read(r, unlimited + ".onHandAtEnd").get(0)).compareTo(new BigDecimal("4")));

        getAs(waiter, "/api/daily-menus/{id}/ingredient-usage", todayId).andExpect(status().isForbidden());
    }

    /** Hợp đồng Menu ↔ Order: Waiter dùng dailyMenuItemId của /today để gọi món; suất đã chiếm phản ánh ngay. */
    @Test
    void todayMenuQuotaPauseAndOrderIntegration() throws Exception {
        long type = data.dish(1000, null, 10).kitchenTypeId();          // bảo đảm đã có thực đơn hôm nay + tồn bếp
        long item = createItem(createCategory(uniq("Nhóm HN")), uniq("Cơm rang"), 35000);
        postJson(manager, "/api/menu-items/{id}/recipes", "{\"ingredients\":[{\"kitchenIngredientTypeId\":" + type + ",\"quantityPerServing\":1}]}", item);
        long todayId = readLong(getAs(waiter, "/api/daily-menus/today").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED")), "$.data.id");
        java.util.List<Number> ids = read(postJson(manager, "/api/daily-menus/{id}/items", "{\"menuItemId\":" + item + ",\"quotaTotal\":2}", todayId)
                .andExpect(status().isCreated()), "$.data.items[?(@.menuItemId == " + item + ")].dailyMenuItemId");
        long dmi = ids.get(0).longValue();
        getAs(waiter, "/api/daily-menus/today")
                .andExpect(jsonPath("$.data.items[?(@.dailyMenuItemId == " + dmi + ")].remaining").value(org.hamcrest.Matchers.hasItem(2)));

        long[] t = openTable();
        long orderItem = submitOne(t[2], dmi, 1);
        postEmpty(kitchen, "/api/order-items/{id}/accept", orderItem).andExpect(jsonPath("$.data.result").value("ACCEPTED"));
        getAs(waiter, "/api/daily-menus/today")
                .andExpect(jsonPath("$.data.items[?(@.dailyMenuItemId == " + dmi + ")].quotaUsed").value(org.hamcrest.Matchers.hasItem(1)))
                .andExpect(jsonPath("$.data.items[?(@.dailyMenuItemId == " + dmi + ")].remaining").value(org.hamcrest.Matchers.hasItem(1)));

        putJson(manager, "/api/daily-menu-items/{id}", "{\"quotaTotal\":0}", dmi)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("RC409"));
        putJson(manager, "/api/daily-menu-items/{id}", "{\"quotaTotal\":null}", dmi)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.remaining").doesNotExist());
        mvc.perform(delete("/api/daily-menu-items/{id}", dmi).header("Authorization", manager))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("RC409"));

        postJson(waiter, "/api/daily-menu-items/{id}/pause", reason("Hết gạo"), dmi).andExpect(status().isForbidden());
        postJson(kitchen, "/api/daily-menu-items/{id}/pause", reason("Hết gạo"), dmi).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paused").value(true)).andExpect(jsonPath("$.data.pauseReason").value("Hết gạo"));
        postJson(kitchen, "/api/daily-menu-items/{id}/pause", reason("lần 2"), dmi)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ALREADY_PAUSED"));
        submit(t[2], "pause-" + dmi, dmi, 1).andExpect(status().isConflict());
        postEmpty(kitchen, "/api/daily-menu-items/{id}/resume", dmi).andExpect(status().isOk()).andExpect(jsonPath("$.data.paused").value(false));
        submit(t[2], "resume-" + dmi, dmi, 1).andExpect(status().isCreated());
    }
}
