package com.restaurant.core.security;

import com.restaurant.core.TestData;
import com.restaurant.core.IntegrationTestBase;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
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

    /** Sai 5 lần thì khóa tạm tên đăng nhập đó (429), kể cả khi lần sau nhập đúng; tài khoản khác không bị ảnh hưởng. */
    @Test
    void repeatedWrongPasswordsTemporarilyBlockThatUsername() throws Exception {
        TestData.Staff target = data.staff("WAITER");
        TestData.Staff other = data.staff("WAITER");
        for (int i = 0; i < 5; i++) {
            login("{\"username\":\"" + target.username() + "\",\"password\":\"sai-" + i + "\"}")
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
        }
        login("{\"username\":\"" + target.username() + "\",\"password\":\"" + TestData.PASSWORD + "\"}")
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.error.code").value("TOO_MANY_ATTEMPTS"));
        login("{\"username\":\"" + other.username() + "\",\"password\":\"" + TestData.PASSWORD + "\"}")
                .andExpect(status().isOk());
    }

    /**
     * Bộ đếm nằm trong database nên mọi bản app thấy cùng một trạng thái khóa: bản khác (hoặc bản vừa khởi động lại) đọc
     * đúng dòng này. Đăng nhập đúng thì xóa bộ đếm.
     */
    @Test
    void failureCounterIsStoredInDatabaseAndClearedOnSuccess() throws Exception {
        TestData.Staff cashier = data.staff("CASHIER");
        String key = cashier.username().trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < 2; i++) {
            login("{\"username\":\" " + cashier.username().toUpperCase(Locale.ROOT) + " \",\"password\":\"sai\"}")
                    .andExpect(status().isUnauthorized());
        }
        Integer failures = admin().sql("SELECT failures FROM login_throttles WHERE username_key = ?").param(key)
                .query(Integer.class).single();
        assertThat(failures).isEqualTo(2);
        login("{\"username\":\"" + cashier.username() + "\",\"password\":\"" + TestData.PASSWORD + "\"}")
                .andExpect(status().isOk());
        assertThat(admin().sql("SELECT count(*) FROM login_throttles WHERE username_key = ?")
                .param(key).query(Long.class).single()).isZero();
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
