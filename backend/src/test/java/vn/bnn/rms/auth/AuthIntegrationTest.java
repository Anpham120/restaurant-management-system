package vn.bnn.rms.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import vn.bnn.rms.IntegrationTest;

/** US-01, US-02, US-03. */
class AuthIntegrationTest extends IntegrationTest {

    @Test
    void loginReturnsTokenAndRole() throws Exception {
        post("/api/auth/login", null, Map.of("username", "admin", "password", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("ADMIN"));
    }

    @Test
    void wrongPasswordDoesNotSayWhichPartWasWrong() throws Exception {
        ResultActions result = post("/api/auth/login", null, Map.of("username", "admin", "password", "nhamroi"))
                .andExpect(status().isUnauthorized());
        assertThat(body(result)).contains("Sai tên đăng nhập hoặc mật khẩu");
    }

    @Test
    void onlyAdminManagesEmployees() throws Exception {
        Map<String, Object> newWaiter = Map.of("fullName", "Nhân viên mới", "username", unique("nv"),
                "role", "WAITER", "password", "matkhau1");
        post("/api/employees", as("phucvu"), newWaiter).andExpect(status().isForbidden());
        post("/api/employees", as("quanly"), newWaiter).andExpect(status().isForbidden());
        post("/api/employees", as("admin"), newWaiter).andExpect(status().isCreated());
        post("/api/employees", as("admin"), newWaiter).andExpect(status().isConflict());
    }

    @Test
    void lockingAnAccountTakesEffectImmediately() throws Exception {
        String username = unique("nv");
        long id = readLong(post("/api/employees", as("admin"), Map.of("fullName", "Sắp nghỉ", "username", username,
                "role", "WAITER", "password", "matkhau1")).andExpect(status().isCreated()), "$.id");
        String token = login(username, "matkhau1");
        get("/api/auth/me", token).andExpect(status().isOk());

        patch("/api/employees/" + id + "/active", as("admin"), Map.of("active", false)).andExpect(status().isOk());

        get("/api/auth/me", token).andExpect(status().isUnauthorized());
        ResultActions relogin = post("/api/auth/login", null, Map.of("username", username, "password", "matkhau1"))
                .andExpect(status().isForbidden());
        assertThat(body(relogin)).contains("Tài khoản đã bị khoá");
    }

    @Test
    void resetPasswordLetsEmployeeSignInWithNewOne() throws Exception {
        String username = unique("nv");
        long id = readLong(post("/api/employees", as("admin"), Map.of("fullName", "Quên mật khẩu",
                "username", username, "role", "CHEF", "password", "matkhau1")).andExpect(status().isCreated()), "$.id");
        post("/api/employees/" + id + "/reset-password", as("admin"), Map.of("newPassword", "matkhau2"))
                .andExpect(status().isNoContent());
        post("/api/auth/login", null, Map.of("username", username, "password", "matkhau1"))
                .andExpect(status().isUnauthorized());
        login(username, "matkhau2");
    }

    @Test
    void requestsWithoutTokenAreRejected() throws Exception {
        get("/api/tables", null).andExpect(status().isUnauthorized());
    }
}
