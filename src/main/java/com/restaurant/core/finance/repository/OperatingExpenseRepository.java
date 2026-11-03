package com.restaurant.core.finance.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.restaurant.core.finance.entity.OperatingExpense;

public interface OperatingExpenseRepository extends JpaRepository<OperatingExpense, Long> {

    Optional<OperatingExpense> findByRequestKey(String requestKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM OperatingExpense e WHERE e.id = :id")
    Optional<OperatingExpense> findByIdForUpdate(@Param("id") Long id);
}
