package com.restaurant.core.menu;

import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.restaurant.core.order.OrderApiTestBase;

import static org.hamcrest.Matchers.hasSize;
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
