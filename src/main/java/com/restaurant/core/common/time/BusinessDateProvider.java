package com.restaurant.core.common.time;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

/**
 * Ngày kinh doanh của quán: giờ Việt Nam, ngày mới bắt đầu lúc 04:00 sáng (ca tối bán qua nửa đêm vẫn tính cho
 * ngày hôm trước). Mọi nghiệp vụ theo ngày (thực đơn ngày, thanh toán, chi phí, đối soát) dùng chung hàm này.
 */
@Component
public class BusinessDateProvider {

    public static final ZoneId RESTAURANT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Duration DAY_STARTS_AFTER_MIDNIGHT = Duration.ofHours(4);

    private final Clock clock;

    public BusinessDateProvider() {
        this(Clock.systemUTC());
    }

    BusinessDateProvider(Clock clock) {
        this.clock = clock;
    }

    public Instant now() {
        return clock.instant();
    }

    public LocalDate today() {
        return businessDateOf(now());
    }

    public static LocalDate businessDateOf(Instant at) {
        return at.atZone(RESTAURANT_ZONE).minus(DAY_STARTS_AFTER_MIDNIGHT).toLocalDate();
    }

    /** Thời điểm bắt đầu một ngày kinh doanh (04:00 giờ Việt Nam). Ngày D kéo dài [startOf(D), startOf(D + 1)). */
    public static Instant startOf(LocalDate businessDate) {
        return businessDate.atStartOfDay(RESTAURANT_ZONE).plus(DAY_STARTS_AFTER_MIDNIGHT).toInstant();
    }
}
