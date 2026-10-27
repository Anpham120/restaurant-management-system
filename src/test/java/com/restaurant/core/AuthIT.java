package com.restaurant.core;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIT extends IntegrationTestBase {

    private org.springframework.test.web.servlet.ResultActions login(String body) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void loginReturnsJwtWithRolesFromDatabase() throws Exception {
        TestData.Staff waiter = data.staff("WAITER");
        login("{\"username\":\"" + waiter.username() + "\",\"password\":\"" + TestData.PASSWORD + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.employeeId").value(waiter.employeeId()))
                .andExpect(jsonPath("$.data.roles[0]").value("WAITER"))
                .andExpect(jsonPath("$.data.mustChangePassword").value(false))
                .andExpect(jsonPath("$.data.accessToken").isString());
    }

    @Test
    void meUsesIdentityFromToken() throws Exception {
        TestData.Staff cashier = data.staff("CASHIER");
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(cashier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.employeeId").value(cashier.employeeId()))
                .andExpect(jsonPath("$.data.username").value(cashier.username()))
                .andExpect(jsonPath("$.data.roles[0]").value("CASHIER"));
    }

    @Test
    void wrongPasswordUnknownUserAndLockedAccountAllReturnSame401() throws Exception {
        TestData.Staff kitchen = data.staff("KITCHEN");
        login("{\"username\":\"" + kitchen.username() + "\",\"password\":\"sai-mat-khau\"}")
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
        login("{\"username\":\"khong-ton-tai\",\"password\":\"x\"}")
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
        String token = bearer(kitchen);
        data.lockAccount(kitchen);
        // token còn hạn nhưng tài khoản đã khóa → 401 ở mọi endpoint
        mvc.perform(get("/api/auth/me").header("Authorization", token))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        login("{\"username\":\"" + kitchen.username() + "\",\"password\":\"" + TestData.PASSWORD + "\"}")
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void invalidBodyIs400WithFieldDetails() throws Exception {
        login("{\"username\":\"\",\"password\":\"x\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("username"));
        login("{khong phai json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }
}
