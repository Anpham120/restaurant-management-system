package com.restaurant.core.order.service.impl;

import java.util.Collection;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.restaurant.core.inventory.OrderItemStockFlags;
import com.restaurant.core.order.entity.OrderItem;
import com.restaurant.core.order.repository.OrderItemRepository;

/** Cài đặt cho module Kho: khóa món và bật cờ "lô đang giữ bị hỏng". */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class OrderItemStockFlagsImpl implements OrderItemStockFlags {

    private final OrderItemRepository orderItemRepository;

    @Override
    public void lockOrderItems(Collection<Long> orderItemIds) {
        if (!orderItemIds.isEmpty()) {
            orderItemRepository.findAllByIdForUpdate(orderItemIds);
        }
    }

    @Override
    public void markStockException(Collection<Long> orderItemIds) {
        if (!orderItemIds.isEmpty()) {
            orderItemRepository.findAllByIdForUpdate(orderItemIds).forEach(OrderItem::markStockException);
            orderItemRepository.flush();
        }
    }
}
