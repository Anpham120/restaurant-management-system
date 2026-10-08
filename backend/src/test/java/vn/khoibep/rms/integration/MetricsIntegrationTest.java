package vn.khoibep.rms.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import vn.khoibep.rms.IntegrationTest;

/** P0-07: what the monitoring agent reads (NFR-11, NFR-12), and the business counters behind its alerts. */
class MetricsIntegrationTest extends IntegrationTest {

    private static final String PROMETHEUS = "/actuator/prometheus";

    @Test
    void onlyTheMetricsTokenOpensThePrometheusEndpoint() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get(PROMETHEUS)).andExpect(status().isForbidden());
        mvc.perform(MockMvcRequestBuilders.get(PROMETHEUS).header("Authorization", "Bearer sai-ma"))
                .andExpect(status().isForbidden());
        // A sign-in is not enough, not even an ADMIN one: this endpoint is for the agent only.
        get(PROMETHEUS, as("admin")).andExpect(status().isForbidden());

        scrape().andExpect(status().isOk());
    }

    @Test
    void countsFailedDeliveriesAndUnmatchedTransfers() throws Exception {
        double failed = value("rms_sepay_webhook_failed_total");
        double unmatched = value("rms_bank_transactions_unmatched_total");

        sepay("sai-khoa", newTxnId(), "KB", 10_000, "in").andExpect(status().isUnauthorized());
        assertThat(value("rms_sepay_webhook_failed_total")).isEqualTo(failed + 1);
        assertThat(value("rms_sepay_webhook_consecutive_failures")).isGreaterThanOrEqualTo(1);

        // BR-16: money with no payment code in it is kept for the cashier, and the delivery itself worked.
        sepay(SEPAY_KEY, newTxnId(), "chuyen tien an toi", 10_000, "in").andExpect(status().isOk());
        assertThat(value("rms_bank_transactions_unmatched_total")).isEqualTo(unmatched + 1);
        assertThat(value("rms_sepay_webhook_consecutive_failures")).isZero();
        assertThat(value("rms_sepay_webhook_failed_total")).isEqualTo(failed + 1);
    }

    @Test
    void timesRequestsInBucketsForTheP95Alert() throws Exception {
        get("/actuator/health", null).andExpect(status().isOk());

        assertThat(text()).contains("http_server_requests_seconds_bucket{").contains("le=\"0.5\"");
    }

    private ResultActions scrape() throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(PROMETHEUS).header("Authorization", "Bearer " + METRICS_TOKEN));
    }

    private String text() throws Exception {
        return scrape().andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    /** The sum of every series of one metric, as Prometheus would read it. */
    private double value(String metric) throws Exception {
        return text().lines()
                .filter(line -> line.startsWith(metric + " ") || line.startsWith(metric + "{"))
                .mapToDouble(line -> Double.parseDouble(line.substring(line.lastIndexOf(' ') + 1)))
                .sum();
    }
}
