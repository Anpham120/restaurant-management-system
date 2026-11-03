package com.restaurant.core.inventory.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.restaurant.core.inventory.entity.StockReturn;

public interface StockReturnRepository extends JpaRepository<StockReturn, Long> {

    Optional<StockReturn> findByRequestKey(String requestKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM StockReturn d WHERE d.id = :id")
    Optional<StockReturn> findByIdForUpdate(@Param("id") Long id);
}
