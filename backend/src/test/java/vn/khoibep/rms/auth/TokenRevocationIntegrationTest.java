package vn.khoibep.rms.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.AuthenticationException;
import org.springframework.test.web.servlet.ResultActions;

import vn.khoibep.rms.IntegrationTest;
import vn.khoibep.rms.config.StompAuthInterceptor;

/**
 * P3-02: revoking sessions (FR-01.6, FR-01.7, US-36, BR-41). Only new accounts here: the demo accounts' tokens are
 * cached for the whole run.
 */
class TokenRevocationIntegrationTest extends IntegrationTest {

    @Autowired
    private StompAuthInterceptor stomp;

    @Test
    void changingThePasswordEndsTheOtherSessionsAndRenewsThisOne() throws Exception {
        EmployeeRef cashier = newEmployee("CASHIER", "HOURLY", 25_000);
        String phoneA = login(cashier.username(), PASSWORD);
        String phoneB = login(cashier.username(), PASSWORD);

        // US-36 AC1
        ResultActions changed = post("/api/auth/change-password", phoneA,
                Map.of("currentPassword", PASSWORD, "newPassword", "matkhau-moi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value(cashier.username()));
        String renewed = read(changed, "$.token");
        get("/api/auth/me", phoneB).andExpect(status().isUnauthorized());
        get("/api/auth/me", phoneA).andExpect(status().isUnauthorized());
        get("/api/auth/me", renewed).andExpect(status().isOk());
        login(cashier.username(), "matkhau-moi");
    }

    @Test
    void anAdminResetEndsEverySession() throws Exception {
        EmployeeRef chef = newEmployee("CHEF", "HOURLY", 25_000);
        String token = login(chef.username(), PASSWORD);

        // US-36 AC2
        post("/api/employees/" + chef.id() + "/reset-password", as("admin"), Map.of("newPassword", "matkhau2"))
                .andExpect(status().isNoContent());
        get("/api/auth/me", token).andExpect(status().isUnauthorized());
    }

    @Test
    void loggingOutEverywhereEndsThisSessionTooUntilTheNextSignIn() throws Exception {
        EmployeeRef waiter = newEmployee("WAITER", "HOURLY", 25_000);
        String here = login(waiter.username(), PASSWORD);
        String elsewhere = login(waiter.username(), PASSWORD);

        // US-36 AC3
        post("/api/auth/logout-all", here, null).andExpect(status().isNoContent());
        get("/api/auth/me", here).andExpect(status().isUnauthorized());
        get("/api/tables", elsewhere).andExpect(status().isUnauthorized());
        get("/api/tables", login(waiter.username(), PASSWORD)).andExpect(status().isOk());
    }

    @Test
    void aRevokedTokenCannotListenToRealtime() throws Exception {
        EmployeeRef waiter = newEmployee("WAITER", "HOURLY", 25_000);
        String token = login(waiter.username(), PASSWORD);
        assertThatCode(() -> connect(token)).doesNotThrowAnyException();

        // US-36 AC4
        post("/api/auth/logout-all", token, null).andExpect(status().isNoContent());
        assertThatThrownBy(() -> connect(token)).isInstanceOf(AuthenticationException.class);
    }

    private void connect(String token) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer " + token);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        stomp.preSend(message, null);
    }
}
