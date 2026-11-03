package com.restaurant.core.menu.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.restaurant.core.menu.entity.DailyMenuItem;

public interface DailyMenuItemRepository extends JpaRepository<DailyMenuItem, Long> {

    List<DailyMenuItem> findByDailyMenuId(Long dailyMenuId);

    boolean existsByDailyMenuId(Long dailyMenuId);

    @Query("SELECT d FROM DailyMenuItem d JOIN FETCH d.dailyMenu JOIN FETCH d.menuItem WHERE d.id = :id")
    Optional<DailyMenuItem> findWithMenuById(@Param("id") Long id);

    /** SELECT … FOR UPDATE: hai món cùng loại được nhận cùng lúc thì kiểm hạn mức lần lượt. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DailyMenuItem d WHERE d.id = :id")
    Optional<DailyMenuItem> findByIdForUpdate(@Param("id") Long id);
}
