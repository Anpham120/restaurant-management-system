package com.restaurant.core.menu.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.restaurant.core.menu.entity.DailyMenu;
import com.restaurant.core.menu.entity.DailyMenuStatus;

public interface DailyMenuRepository extends JpaRepository<DailyMenu, Long> {

    List<DailyMenu> findByStatusAndBusinessDateBefore(DailyMenuStatus status, LocalDate businessDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM DailyMenu m WHERE m.id = :id")
    Optional<DailyMenu> findByIdForUpdate(@Param("id") Long id);
}
