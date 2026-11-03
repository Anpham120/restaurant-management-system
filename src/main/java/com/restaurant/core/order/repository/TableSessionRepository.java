package com.restaurant.core.order.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.restaurant.core.order.entity.TableSession;

public interface TableSessionRepository extends JpaRepository<TableSession, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM TableSession s WHERE s.id = :id")
    Optional<TableSession> findByIdForUpdate(@Param("id") Long id);
}
