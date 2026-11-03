package com.restaurant.core.order.service.impl;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.restaurant.core.menu.QuotaUsageCounter;
import com.restaurant.core.order.entity.OrderItemStatus;
import com.restaurant.core.order.repository.OrderItemRepository;

/** Cài đặt cho module Thực đơn: đếm suất đã dùng từ các món đã gọi. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuotaUsageCounterImpl implements QuotaUsageCounter {

    private final OrderItemRepository orderItemRepository;

    @Override
    public long countUsedPortions(long dailyMenuItemId) {
        return countUsedPortions(List.of(dailyMenuItemId)).get(dailyMenuItemId);
    }

    @Override
    public Map<Long, Long> countUsedPortions(Collection<Long> dailyMenuItemIds) {
        Map<Long, Long> used = new HashMap<>();
        dailyMenuItemIds.forEach(id -> used.put(id, 0L));
        if (dailyMenuItemIds.isEmpty()) {
            return used;
        }
        for (Object[] row : orderItemRepository.sumUsedPortions(dailyMenuItemIds, OrderItemStatus.USING_QUOTA)) {
            used.put((Long) row[0], ((Number) row[1]).longValue());
        }
        return used;
    }

    @Override
    public boolean hasBeenOrdered(long dailyMenuItemId) {
        return orderItemRepository.existsByDailyMenuItemId(dailyMenuItemId);
    }
}
