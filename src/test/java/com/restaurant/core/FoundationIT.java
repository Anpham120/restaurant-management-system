package com.restaurant.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.restaurant.core.common.exception.ApiException;
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

    /** Nghiệp vụ nằm ở Service nên ứng dụng ghi trực tiếp bảng nghiệp vụ, nhưng vẫn không được sửa cấu trúc DB hay danh mục vai trò. */
    @Test
    void appCannotChangeSchemaOrRoles() {
        for (String sql : new String[] {
                "UPDATE roles SET role_name = 'x' WHERE false",
                "CREATE TABLE hack (id int)",
                "ALTER TABLE invoices ADD COLUMN hack int",
                "DROP TRIGGER trg_invoice_guard ON invoices"}) {
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
    void missingRequiredQueryParamIs400NotServerError() throws Exception {
        String token = bearer(data.staff("MANAGER"));
        mvc.perform(get("/api/reports/revenue-by-day?from=2026-10-01").header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("to"));
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
        tableService.getAllTables(false);
        assertThat(output).contains("TableServiceImpl.getAllTables");
        assertThatThrownBy(() -> tableService.openTableSession(987654321L, 987654321L)).isInstanceOf(ApiException.class);
        assertThat(output).contains("TableServiceImpl.openTableSession lỗi sau").doesNotContain("987654321");
    }
}
