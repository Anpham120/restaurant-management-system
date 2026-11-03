package com.restaurant.core.inventory.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.restaurant.core.inventory.entity.StockCount;

public interface StockCountRepository extends JpaRepository<StockCount, Long> {

    Optional<StockCount> findByRequestKey(String requestKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM StockCount d WHERE d.id = :id")
    Optional<StockCount> findByIdForUpdate(@Param("id") Long id);
}
