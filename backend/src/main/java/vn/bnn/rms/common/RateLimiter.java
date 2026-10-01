package vn.bnn.rms.common;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.stereotype.Component;

/**
 * Request allowances per key (BR-30, BR-31): a token bucket per table or per user name, kept in this server's
 * memory and forgotten after 10 idle minutes. One server is enough for one restaurant; several servers would need
 * a shared store.
 */
@Component
public class RateLimiter {

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(100_000)
            .build();

    /**
     * Uses one of the key's {@code perMinute} requests a minute, or throws 429. Used requests come back one by one
     * over the minute. The message gets the seconds to wait through its %d.
     *
     * @param key names what is limited and whose allowance it is, for example "table:12"
     */
    public void acquire(String key, int perMinute, String message) {
        Bucket bucket = buckets.get(key, k -> Bucket.builder()
                .addLimit(limit -> limit.capacity(perMinute).refillGreedy(perMinute, Duration.ofMinutes(1)))
                .build());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long seconds = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1;
            throw new TooManyRequestsException(message.formatted(seconds), seconds);
        }
    }
}
