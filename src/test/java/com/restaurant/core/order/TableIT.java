package com.restaurant.core.order;

import org.junit.jupiter.api.Test;

import com.restaurant.core.IntegrationTestBase;
import com.restaurant.core.TestData;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Endpoint mẫu: đọc dữ liệu thật, gọi rc_open_session, ánh xạ RC409/RC422/RC403 và @PreAuthorize. */
class TableIT extends IntegrationTestBase {

    @Test
    void waiterListsTablesKitchenIsForbidden() throws Exception {
        long table = data.table();
        mvc.perform(get("/api/tables").header("Authorization", bearer(data.staff("WAITER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + table + ")].status").value("AVAILABLE"));
        mvc.perform(get("/api/tables").header("Authorization", bearer(data.staff("KITCHEN"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void openSessionThenConflictFromDatabase() throws Exception {
        long table = data.table();
        String waiter = bearer(data.staff("WAITER"));
        mvc.perform(post("/api/tables/{id}/sessions", table).header("Authorization", waiter))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tableId").value(table))
                .andExpect(jsonPath("$.data.sessionId").isNumber());
        mvc.perform(post("/api/tables/{id}/sessions", table).header("Authorization", waiter))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("RC409"))
                .andExpect(jsonPath("$.error.message").value(containsString("đang có khách")));
    }

    @Test
    void unknownTableIs422FromDatabase() throws Exception {
        mvc.perform(post("/api/tables/{id}/sessions", 999999999L).header("Authorization", bearer(data.staff("MANAGER"))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("RC422"));
    }

    @Test
    void cashierCannotOpenSession() throws Exception {
        mvc.perform(post("/api/tables/{id}/sessions", data.table()).header("Authorization", bearer(data.staff("CASHIER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    /** Token còn hạn nhưng tài khoản vừa bị khóa: @PreAuthorize cho qua, DB (rc_authorize) vẫn chặn → RC403. */
    @Test
    void databaseRechecksActorAfterAccountLocked() throws Exception {
        TestData.Staff waiter = data.staff("WAITER");
        String token = bearer(waiter);
        data.lockAccount(waiter);
        mvc.perform(post("/api/tables/{id}/sessions", data.table()).header("Authorization", token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("RC403"));
    }

    private org.springframework.test.web.servlet.ResultActions json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,
                                                                    String token, String body) throws Exception {
        return mvc.perform(req.header("Authorization", token).contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void managerCreatesAndUpdatesTables() throws Exception {
        String manager = bearer(data.staff("MANAGER"));
        String code = "QL" + System.nanoTime() % 100000;
        String body = mvc.perform(post("/api/tables").header("Authorization", manager)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\",\"capacity\":4}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value(code))
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.data.id")).longValue();
        json(post("/api/tables"), manager, "{\"code\":\"" + code + "\",\"capacity\":2}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("DUPLICATE"));
        json(post("/api/tables"), manager, "{\"code\":\"\",\"capacity\":0}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        json(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/tables/{id}", id), manager,
                "{\"code\":\"" + code + "V\",\"capacity\":6}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.capacity").value(6)).andExpect(jsonPath("$.data.code").value(code + "V"));
        json(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/tables/{id}", 999999999L), manager,
                "{\"code\":\"X1\",\"capacity\":2}").andExpect(status().isNotFound());
        json(post("/api/tables"), bearer(data.staff("WAITER")), "{\"code\":\"W1X\",\"capacity\":2}").andExpect(status().isForbidden());
    }

    /** Ngừng sử dụng chỉ khi bàn trống; bàn ngừng thì không mở phiên được, ẩn khỏi danh sách thường. */
    @Test
    void deactivateOnlyAvailableTables() throws Exception {
        String manager = bearer(data.staff("MANAGER"));
        String waiter = bearer(data.staff("WAITER"));
        long busy = data.table();
        mvc.perform(post("/api/tables/{id}/sessions", busy).header("Authorization", waiter)).andExpect(status().isCreated());
        mvc.perform(post("/api/tables/{id}/deactivate", busy).header("Authorization", manager))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("TABLE_OCCUPIED"));

        long free = data.table();
        mvc.perform(post("/api/tables/{id}/deactivate", free).header("Authorization", manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.active").value(false));
        mvc.perform(post("/api/tables/{id}/sessions", free).header("Authorization", waiter))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.error.code").value("RC422"));
        mvc.perform(get("/api/tables").header("Authorization", waiter))
                .andExpect(jsonPath("$.data[?(@.id == " + free + ")]").doesNotExist());
        mvc.perform(get("/api/tables?includeInactive=true").header("Authorization", waiter))
                .andExpect(jsonPath("$.data[?(@.id == " + free + ")]").doesNotExist());
        mvc.perform(get("/api/tables?includeInactive=true").header("Authorization", manager))
                .andExpect(jsonPath("$.data[?(@.id == " + free + ")].active").value(org.hamcrest.Matchers.hasItem(false)));
        mvc.perform(post("/api/tables/{id}/activate", free).header("Authorization", manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.active").value(true));
        mvc.perform(post("/api/tables/{id}/sessions", free).header("Authorization", waiter)).andExpect(status().isCreated());
    }
}
