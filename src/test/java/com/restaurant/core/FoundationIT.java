package com.restaurant.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.restaurant.core.order.service.TableService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Khởi động, health, quyền DB của tài khoản ứng dụng, 401/404 và AOP. */
@ExtendWith(OutputCaptureExtension.class)
class FoundationIT extends IntegrationTestBase {

    @Autowired
    JdbcClient appJdbc;

    @Autowired
    TableService tableService;

    @Test
    void healthIsUpWithoutToken() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void appConnectsAsRcAppMemberNotSuperuser() {
        assertThat(appJdbc.sql("SELECT current_user").query(String.class).single()).isEqualTo(APP_USER);
        assertThat(appJdbc.sql("SELECT rolsuper FROM pg_roles WHERE rolname = current_user").query(Boolean.class).single()).isFalse();
        assertThat(appJdbc.sql("SELECT pg_has_role(current_user, 'rc_app', 'MEMBER')").query(Boolean.class).single()).isTrue();
        assertThat(appJdbc.sql("SELECT pg_has_role(current_user, 'rc_owner', 'MEMBER')").query(Boolean.class).single()).isFalse();
    }

    @Test
    void appCannotBypassBusinessFunctions() {
        for (String sql : new String[] {
                "UPDATE invoices SET status = 'PAID' WHERE false",
                "INSERT INTO payments (invoice_id, payment_request_id, amount, method, business_date) SELECT 1, 1, 1, 'CASH', CURRENT_DATE WHERE false",
                "UPDATE kitchen_batches SET quantity_on_hand = 0 WHERE false",
                "SELECT rc_item_transition(1, ARRAY['PENDING'], 'ACCEPTED', 1)",
                "SELECT rc_process_verified_qr('x', 'y', 1, '{}')"}) {
            assertThatThrownBy(() -> appJdbc.sql(sql).update())
                    .as(sql)
                    .isInstanceOf(DataAccessException.class)
                    .satisfies(ex -> assertThat(sqlState(ex)).as(sql).isEqualTo("42501"));   // insufficient_privilege
        }
    }

    private static String sqlState(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof java.sql.SQLException sql) {
                return sql.getSQLState();
            }
        }
        return null;
    }

    @Test
    void protectedEndpointWithoutTokenIs401Json() throws Exception {
        mvc.perform(get("/api/tables"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/tables").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void unknownPathIs404Json() throws Exception {
        String token = bearer(data.staff("MANAGER"));
        mvc.perform(get("/api/khong-ton-tai").header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void performanceAspectLogsServiceCallsWithoutArguments(CapturedOutput output) {
        tableService.listTables(false);
        assertThat(output).contains("TableService.listTables");
        assertThatThrownBy(() -> tableService.openSession(987654321L, 987654321L)).isInstanceOf(DataAccessException.class);
        assertThat(output).contains("TableService.openSession lỗi sau").doesNotContain("987654321");
    }
}
