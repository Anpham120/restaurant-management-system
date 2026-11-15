package com.restaurant.core.menu.repository;

import java.util.Collection;
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

    /**
     * Khóa nhiều món theo id tăng dần (PostgreSQL khóa theo thứ tự trả về), nên hai thao tác cùng sửa nhiều món không
     * khóa chéo nhau. Đọc sau khi khóa: không ghi đè trạng thái tạm dừng mà người khác vừa đổi.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DailyMenuItem d WHERE d.id IN :ids ORDER BY d.id")
    List<DailyMenuItem> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    /** Mọi món của một thực đơn ngày, khóa theo id tăng dần. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DailyMenuItem d WHERE d.dailyMenu.id = :dailyMenuId ORDER BY d.id")
    List<DailyMenuItem> findByDailyMenuIdForUpdate(@Param("dailyMenuId") Long dailyMenuId);
}
