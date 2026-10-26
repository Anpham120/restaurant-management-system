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
}
