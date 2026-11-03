package com.restaurant.core.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.restaurant.core.order.entity.OrderItemEvent;

public interface OrderItemEventRepository extends JpaRepository<OrderItemEvent, Long> {
}
