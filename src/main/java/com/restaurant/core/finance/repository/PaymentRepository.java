package com.restaurant.core.finance.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.restaurant.core.finance.entity.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
}
