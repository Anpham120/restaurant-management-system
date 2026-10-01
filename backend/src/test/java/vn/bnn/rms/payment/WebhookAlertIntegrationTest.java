package vn.bnn.rms.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import vn.bnn.rms.IntegrationTest;
import vn.bnn.rms.common.realtime.RealtimeEvent;

/** P3-05: warning when SePay deliveries keep failing (FR-08.8, US-19 AC3, AC4, BR-32), and the metrics (NFR-11). */
@RecordApplicationEvents
class WebhookAlertIntegrationTest extends IntegrationTest {

    private static final String STATUS = "/api/bank-transactions/webhook-status";

    @Autowired
    private ApplicationEvents events;

    @Test
    void threeFailuresInARowWarnTheCashierUntilADeliveryWorks() throws Exception {
        // Other tests send bad deliveries too, so start from one that works.
        deliverOk();
        get(STATUS, as("thungan"))
                .andExpect(jsonPath("$.failing").value(false))
                .andExpect(jsonPath("$.failures").value(0))
                .andExpect(jsonPath("$.since").doesNotExist());

        sepay("sai-khoa", newTxnId(), "BNN", 10_000, "in").andExpect(status().isUnauthorized());
        String since = read(get(STATUS, as("thungan")), "$.since");
        sepay(null, newTxnId(), "BNN", 10_000, "in").andExpect(status().isUnauthorized());
        // Two failures could still be one that SePay's next retry gets through.
        get(STATUS, as("thungan"))
                .andExpect(jsonPath("$.failing").value(false))
                .andExpect(jsonPath("$.failures").value(2));
        assertThat(notices()).isZero();

        // US-19 AC3: the third failure in a row warns the cashier screens.
        deliverWithoutId().andExpect(status().isBadRequest());
        get(STATUS, as("thungan"))
                .andExpect(jsonPath("$.failing").value(true))
                .andExpect(jsonPath("$.failures").value(3))
                .andExpect(jsonPath("$.since").value(since))
                .andExpect(jsonPath("$.lastError").value("Thiếu mã giao dịch"));
        assertThat(notices()).isEqualTo(1);

        // Once per run of failures.
        sepay("sai-khoa", newTxnId(), "BNN", 10_000, "in").andExpect(status().isUnauthorized());
        get(STATUS, as("quanly"))
                .andExpect(jsonPath("$.failures").value(4))
                .andExpect(jsonPath("$.lastError").value("Sai khoá API"));
        assertThat(notices()).isEqualTo(1);

        // US-19 AC4: a delivery that works clears the warning.
        deliverOk();
        get(STATUS, as("thungan"))
                .andExpect(jsonPath("$.failing").value(false))
                .andExpect(jsonPath("$.failures").value(0))
                .andExpect(jsonPath("$.lastError").doesNotExist());
        assertThat(notices()).isEqualTo(2);
    }

    @Test
    void onlyCashiersAndManagersSeeTheWebhookStatus() throws Exception {
        get(STATUS, as("phucvu")).andExpect(status().isForbidden());
        get(STATUS, null).andExpect(status().isUnauthorized());
    }

    @Test
    void onlyAdminsReadTheMetrics() throws Exception {
        sepay("sai-khoa", newTxnId(), "BNN", 10_000, "in").andExpect(status().isUnauthorized());
        deliverOk();

        // Spring Boot counts every request by path and answer, so rejected webhooks show here too.
        get("/actuator/metrics/http.server.requests?tag=uri:/api/webhooks/sepay&tag=status:401", as("admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements[?(@.statistic == 'COUNT')].value")
                        .value(hasItem(greaterThanOrEqualTo(1.0))));
        get("/actuator/metrics", as("quanly")).andExpect(status().isForbidden());
        get("/actuator/metrics", null).andExpect(status().isUnauthorized());
        // The pipeline checks a new version through this one.
        get("/actuator/health", null).andExpect(status().isOk());
    }

    /** An outgoing transfer is stored as ignored and answered with success (BR-16). */
    private void deliverOk() throws Exception {
        sepay(SEPAY_KEY, newTxnId(), "tra tien nha cung cap", 10_000, "out").andExpect(status().isOk());
    }

    private ResultActions deliverWithoutId() throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/webhooks/sepay")
                .header("Authorization", "Apikey " + SEPAY_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"transferType\": \"in\", \"transferAmount\": 10000}"));
    }

    private long notices() {
        return events.stream(RealtimeEvent.class)
                .filter(e -> RealtimeEvent.WEBHOOK_STATUS.equals(e.type()))
                .count();
    }
}
