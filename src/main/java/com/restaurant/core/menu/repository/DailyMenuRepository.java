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

    /** Khóa theo ngày (pg_advisory_xact_lock): hai request cùng tự mở thực đơn hôm nay thì chạy lần lượt, không tạo trùng. */
    @Query(value = "SELECT CAST(pg_advisory_xact_lock(hashtext('daily_menus'), CAST(:date - DATE '2000-01-01' AS int)) AS text)", nativeQuery = true)
    String lockBusinessDate(@Param("date") LocalDate date);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM DailyMenu m WHERE m.id = :id")
    Optional<DailyMenu> findByIdForUpdate(@Param("id") Long id);
}
