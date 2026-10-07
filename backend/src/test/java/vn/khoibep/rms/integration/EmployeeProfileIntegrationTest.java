package vn.khoibep.rms.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import vn.khoibep.rms.IntegrationTest;

/** US-20 and BR-22: profile and pay, which only ADMIN sees; resigning locks the account but keeps the records. */
class EmployeeProfileIntegrationTest extends IntegrationTest {

    @Test
    void negativePayIsRejected() throws Exception {
        EmployeeRef waiter = newEmployee("WAITER", "HOURLY", 25_000);
        put("/api/employees/" + waiter.id() + "/profile", as("admin"), Map.of("payType", "HOURLY", "payRate", -1))
                .andExpect(status().isBadRequest());
        post("/api/employees", as("admin"), Map.of("fullName", "Lương âm", "username", unique("am"),
                        "role", "WAITER", "password", PASSWORD, "payRate", -5))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyAdminsSeeAndChangePay() throws Exception {
        EmployeeRef chef = newEmployee("CHEF", "HOURLY", 25_000);
        String profile = "/api/employees/" + chef.id() + "/profile";

        put(profile, as("admin"), Map.of("phone", "0912 345 678", "hiredOn", "2026-01-15", "payType", "MONTHLY",
                        "payRate", 9_000_000))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("0912 345 678"))
                .andExpect(jsonPath("$.hiredOn").value("2026-01-15"))
                .andExpect(jsonPath("$.payType").value("MONTHLY"))
                .andExpect(jsonPath("$.payRate").value(9_000_000));

        get("/api/employees", as("quanly")).andExpect(status().isForbidden());
        put(profile, as("quanly"), Map.of("payType", "HOURLY", "payRate", 1)).andExpect(status().isForbidden());
        // Sign-in data never carries pay.
        get("/api/auth/me", as(chef.username())).andExpect(jsonPath("$.payRate").doesNotExist());
    }

    @Test
    void resigningLocksTheAccountButKeepsTheProfile() throws Exception {
        EmployeeRef cashier = newEmployee("CASHIER", "HOURLY", 26_000);
        String token = as(cashier.username());

        post("/api/employees/" + cashier.id() + "/resign", as("admin"), Map.of("leftOn", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.leftOn").value("2026-09-30"));

        get("/api/auth/me", token).andExpect(status().isUnauthorized());
        List<Integer> rates = read(get("/api/employees", as("admin")), "$[?(@.id == " + cashier.id() + ")].payRate");
        assertThat(rates).containsExactly(26_000);
    }
}
