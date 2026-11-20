package com.restaurant.core.security.entity;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginThrottleEntryTest {

    private static final int MAX = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Duration LOCK = Duration.ofMinutes(15);
    private static final Instant T0 = Instant.parse("2026-11-02T00:00:00Z");

    private static void fail(LoginThrottleEntry entry, Instant at, int times) {
        for (int i = 0; i < times; i++) {
            entry.recordFailure(at, MAX, WINDOW, LOCK);
        }
    }

    @Test
    void locksAfterMaxFailuresThenCountsAgainAfterLockEnds() {
        LoginThrottleEntry entry = new LoginThrottleEntry();
        fail(entry, T0, MAX - 1);
        assertThat(entry.lockedSecondsAt(T0)).isZero();
        fail(entry, T0, 1);
        assertThat(entry.lockedSecondsAt(T0)).isEqualTo(LOCK.toSeconds());
        Instant afterLock = T0.plus(LOCK);
        assertThat(entry.lockedSecondsAt(afterLock)).isZero();
        assertThat(entry.isExpiredAt(afterLock, WINDOW)).isTrue();
        fail(entry, afterLock, 1);                                   // hết khóa thì đếm lại từ đầu
        assertThat(entry.getFailures()).isEqualTo(1);
        assertThat(entry.lockedSecondsAt(afterLock)).isZero();
    }

    @Test
    void failuresOutsideWindowDoNotAccumulate() {
        LoginThrottleEntry entry = new LoginThrottleEntry();
        fail(entry, T0, MAX - 1);
        fail(entry, T0.plus(WINDOW).plusSeconds(1), 1);
        assertThat(entry.getFailures()).isEqualTo(1);
        assertThat(entry.lockedSecondsAt(T0.plus(WINDOW).plusSeconds(1))).isZero();
    }
}
