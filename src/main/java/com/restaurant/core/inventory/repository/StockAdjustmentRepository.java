package com.restaurant.core.inventory.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.restaurant.core.inventory.entity.StockAdjustment;

public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, Long> {

    Optional<StockAdjustment> findByRequestKey(String requestKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM StockAdjustment d WHERE d.id = :id")
    Optional<StockAdjustment> findByIdForUpdate(@Param("id") Long id);
}
