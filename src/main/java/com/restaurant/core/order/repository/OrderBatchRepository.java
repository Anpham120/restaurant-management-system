package com.restaurant.core.order.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.restaurant.core.order.entity.OrderBatch;

public interface OrderBatchRepository extends JpaRepository<OrderBatch, Long> {

    Optional<OrderBatch> findByRequestKey(String requestKey);
}
