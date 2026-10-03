package vn.khoibep.rms.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** NFR-11, NFR-12: who may read /actuator/prometheus. */
class MetricsTokenTest {

    @Test
    void withoutATokenSetNobodyGetsIn() {
        assertThat(SecurityConfig.metricsTokenMatches(null, "Bearer null")).isFalse();
        assertThat(SecurityConfig.metricsTokenMatches("", "Bearer ")).isFalse();
        assertThat(SecurityConfig.metricsTokenMatches("  ", "Bearer   ")).isFalse();
    }

    @Test
    void onlyTheExactBearerTokenGetsIn() {
        String token = "0123456789abcdef0123456789abcdef";

        assertThat(SecurityConfig.metricsTokenMatches(token, "Bearer " + token)).isTrue();
        assertThat(SecurityConfig.metricsTokenMatches(token, null)).isFalse();
        assertThat(SecurityConfig.metricsTokenMatches(token, token)).isFalse();
        assertThat(SecurityConfig.metricsTokenMatches(token, "Bearer " + token + "x")).isFalse();
        assertThat(SecurityConfig.metricsTokenMatches(token, "Basic " + token)).isFalse();
    }
}
