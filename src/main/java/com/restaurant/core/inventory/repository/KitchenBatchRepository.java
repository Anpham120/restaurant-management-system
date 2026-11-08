package com.restaurant.core.inventory.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.restaurant.core.inventory.entity.KitchenBatch;

/**
 * Mọi câu khóa lô bếp xếp theo (loại nguyên liệu, id) — cùng một thứ tự ở mọi nghiệp vụ để hai giao dịch không chờ
 * nhau vòng tròn (deadlock).
 */
public interface KitchenBatchRepository extends JpaRepository<KitchenBatch, Long> {

    /** Khóa các lô đang dùng được của những loại nguyên liệu cho trước, cộng thêm các lô có id cho trước. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT b FROM KitchenBatch b
             WHERE b.id IN :extraIds
                OR (b.kitchenIngredientType.id IN :typeIds AND b.usable = true AND b.quantityOnHand > 0
                    AND (b.usableUntil IS NULL OR b.usableUntil > :now))
             ORDER BY b.kitchenIngredientType.id, b.id""")
    List<KitchenBatch> lockUsableBatchesOfTypes(@Param("typeIds") Collection<Long> typeIds,
                                                @Param("extraIds") Collection<Long> extraIds,
                                                @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM KitchenBatch b WHERE b.id IN :ids ORDER BY b.kitchenIngredientType.id, b.id")
    List<KitchenBatch> lockBatchesByIds(@Param("ids") Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM KitchenBatch b WHERE b.id = :id")
    Optional<KitchenBatch> findByIdForUpdate(@Param("id") Long id);
}
