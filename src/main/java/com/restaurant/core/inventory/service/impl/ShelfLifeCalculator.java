package com.restaurant.core.inventory.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import com.restaurant.core.common.time.BusinessDateProvider;

/** Hạn dùng của lô ở bếp: sớm hơn giữa hạn của lô gốc (hết ngày hết hạn) và thời gian bảo quản của loại bếp. */
public final class ShelfLifeCalculator {

    private ShelfLifeCalculator() {
    }

    /** null nếu cả hai đều không giới hạn. */
    public static Instant calculateUsableUntil(LocalDate rawExpiryDate, Integer shelfLifeHours, Instant now) {
        Instant byExpiry = rawExpiryDate == null ? null
                : rawExpiryDate.plusDays(1).atStartOfDay(BusinessDateProvider.RESTAURANT_ZONE).toInstant();
        Instant byShelfLife = shelfLifeHours == null ? null : now.plus(Duration.ofHours(shelfLifeHours));
        if (byExpiry == null) {
            return byShelfLife;
        }
        if (byShelfLife == null) {
            return byExpiry;
        }
        return byExpiry.isBefore(byShelfLife) ? byExpiry : byShelfLife;
    }
}
