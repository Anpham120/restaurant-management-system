package com.restaurant.core.security.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginThrottleTest {

    /** Đồng hồ chỉnh tay để thử hết hạn khóa mà không phải chờ. */
    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-11-02T00:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final TestClock clock = new TestClock();
    private final LoginThrottle throttle = new LoginThrottle(clock);

    private void fail(String user, int times) {
        for (int i = 0; i < times; i++) {
            throttle.recordFailure(user);
        }
    }

    @Test
    void locksAfterMaxFailuresThenUnlocksAfterLockTime() {
        fail("manager", LoginThrottle.MAX_FAILURES - 1);
        assertThat(throttle.lockedSeconds("manager")).isZero();
        fail("manager", 1);
        assertThat(throttle.lockedSeconds("Manager ")).isEqualTo(LoginThrottle.LOCK.toSeconds());   // không phân biệt hoa/thường, khoảng trắng
        clock.advance(LoginThrottle.LOCK);
        assertThat(throttle.lockedSeconds("manager")).isZero();
        fail("manager", 1);                                       // hết khóa thì đếm lại từ đầu
        assertThat(throttle.lockedSeconds("manager")).isZero();
    }

    @Test
    void failuresOutsideWindowDoNotAccumulate() {
        fail("waiter", LoginThrottle.MAX_FAILURES - 1);
        clock.advance(LoginThrottle.WINDOW.plusSeconds(1));
        fail("waiter", 1);
        assertThat(throttle.lockedSeconds("waiter")).isZero();
    }

    @Test
    void successResetsCounterAndUsersAreIndependent() {
        fail("cashier", LoginThrottle.MAX_FAILURES - 1);
        throttle.recordSuccess("cashier");
        fail("cashier", LoginThrottle.MAX_FAILURES - 1);
        assertThat(throttle.lockedSeconds("cashier")).isZero();
        fail("kitchen", LoginThrottle.MAX_FAILURES);
        assertThat(throttle.lockedSeconds("kitchen")).isPositive();
        assertThat(throttle.lockedSeconds("cashier")).isZero();
    }
}
