package vn.khoibep.rms;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** The application clock in tests: real time until a test pins it. */
public class MutableClock extends Clock {

    private final ZoneId zone;
    private volatile Instant pinned;

    public MutableClock(ZoneId zone) {
        this.zone = zone;
    }

    /** Pins the clock to a date and time in the restaurant's zone. */
    public void set(LocalDateTime local) {
        pinned = local.atZone(zone).toInstant();
    }

    public void reset() {
        pinned = null;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId other) {
        return other.equals(zone) ? this : Clock.system(other);
    }

    @Override
    public Instant instant() {
        Instant value = pinned;
        return value != null ? value : Instant.now();
    }
}
