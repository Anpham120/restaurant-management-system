package vn.khoibep.rms.config;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String timezone,
        Jwt jwt,
        Sepay sepay,
        String publicBaseUrl,
        List<String> corsAllowedOrigins,
        DemoAccounts demoAccounts,
        InitialAdmin initialAdmin,
        Metrics metrics) {

    public record Jwt(String secret, Duration ttl) {
    }

    public record Sepay(String apiKey) {
    }

    public record DemoAccounts(boolean enabled, String password) {
    }

    /** BR-48: the first admin of a new database without demo accounts; the password empty means none. */
    public record InitialAdmin(String username, String password) {
    }

    /** NFR-12: the token that opens /actuator/prometheus to the monitoring agent; empty means it stays shut. */
    public record Metrics(String token) {
    }

    public ZoneId zoneId() {
        return ZoneId.of(timezone);
    }
}
