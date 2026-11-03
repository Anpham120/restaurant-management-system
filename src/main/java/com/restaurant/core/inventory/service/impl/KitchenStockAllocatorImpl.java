package com.restaurant.core.inventory.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.restaurant.core.common.exception.ApiException;
import com.restaurant.core.common.time.BusinessDateProvider;
import com.restaurant.core.inventory.KitchenStockAllocator;
import com.restaurant.core.inventory.entity.KitchenBatch;
import com.restaurant.core.inventory.entity.MovementType;
import com.restaurant.core.inventory.entity.ReleaseReason;
import com.restaurant.core.inventory.entity.StockMovement;
import com.restaurant.core.inventory.entity.StockReservation;
import com.restaurant.core.inventory.repository.KitchenBatchRepository;
import com.restaurant.core.inventory.repository.StockMovementRepository;
import com.restaurant.core.inventory.repository.StockReservationRepository;
import com.restaurant.core.menu.DailyMenuLookup.RecipeLine;
import com.restaurant.core.menu.DailyMenuLookup;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class KitchenStockAllocatorImpl implements KitchenStockAllocator {

    /** Id không tồn tại, dùng khi danh sách id rỗng (IN () không hợp lệ trong SQL). */
    private static final List<Long> NO_IDS = List.of(-1L);

    private final KitchenBatchRepository kitchenBatchRepository;
    private final StockReservationRepository reservationRepository;
    private final StockMovementRepository movementRepository;
    private final DailyMenuLookup dailyMenuLookup;
    private final BusinessDateProvider clock;

    /** Một phần lấy từ một lô trong kế hoạch giữ nguyên liệu. */
    private record Take(KitchenBatch batch, BigDecimal quantity) {
    }

    @Override
    public Optional<String> reserveIngredientsForItem(long orderItemId, long dailyMenuItemId, int servings) {
        List<RecipeLine> recipe = dailyMenuLookup.getRecipeLines(dailyMenuItemId);
        List<KitchenBatch> lockedBatches = lockCandidateBatches(recipe, NO_IDS);
        return planAndReserve(orderItemId, recipe, servings, lockedBatches);
    }

    @Override
    public void releaseReservationsOfItem(long orderItemId) {
        List<StockReservation> active = lockBatchesOfActiveReservations(orderItemId);
        Instant now = clock.now();
        for (StockReservation reservation : active) {
            reservation.getKitchenBatch().cancelReservation(reservation.getQuantity());
            reservation.release(ReleaseReason.ITEM_CANCELLED, now);
        }
    }

    @Override
    public void consumeReservationsForCooking(long orderItemId) {
        List<StockReservation> active = lockBatchesOfActiveReservations(orderItemId);
        Instant now = clock.now();
        if (active.stream().anyMatch(r -> !r.getKitchenBatch().isUsableAt(now))) {
            throw ApiException.invalidState("Món " + orderItemId + " đang giữ lô nguyên liệu hết hạn hoặc hỏng: cấp lại nguyên liệu trước khi nấu");
        }
        for (StockReservation reservation : active) {
            KitchenBatch batch = reservation.getKitchenBatch();
            batch.consumeReserved(reservation.getQuantity());
            reservation.markConsumed(now);
            movementRepository.save(StockMovement.ofKitchenBatch(MovementType.COOK, batch.getId(), reservation.getQuantity().negate(),
                    "COOK:" + reservation.getId(), now).orderItem(orderItemId));
        }
    }

    @Override
    public void reallocateUnusableReservations(long orderItemId, long dailyMenuItemId, int servings) {
        List<RecipeLine> recipe = dailyMenuLookup.getRecipeLines(dailyMenuItemId);
        List<Long> heldBatchIds = reservationRepository.findActiveBatchIdsOfOrderItem(orderItemId);
        List<KitchenBatch> lockedBatches = lockCandidateBatches(recipe, heldBatchIds.isEmpty() ? NO_IDS : heldBatchIds);
        Instant now = clock.now();
        for (StockReservation reservation : reservationRepository.findActiveOfOrderItem(orderItemId)) {
            KitchenBatch batch = reservation.getKitchenBatch();
            if (!batch.isUsableAt(now)) {
                batch.cancelReservation(reservation.getQuantity());
                reservation.release(ReleaseReason.BATCH_UNUSABLE, now);
            }
        }
        reservationRepository.flush();
        planAndReserve(orderItemId, recipe, servings, lockedBatches).ifPresent(shortage -> {
            throw ApiException.insufficientStock(shortage);
        });
    }

    // ---------- FEFO

    private List<KitchenBatch> lockCandidateBatches(List<RecipeLine> recipe, List<Long> extraBatchIds) {
        List<Long> typeIds = recipe.stream().map(RecipeLine::kitchenIngredientTypeId).toList();
        return kitchenBatchRepository.lockUsableBatchesOfTypes(typeIds.isEmpty() ? NO_IDS : typeIds, extraBatchIds, clock.now());
    }

    /**
     * Lập kế hoạch lấy đủ từng loại nguyên liệu (lô hết hạn sớm nhất trước), chỉ ghi khi đủ tất cả.
     * Phần đã giữ sẵn cho món (khi cấp lại) được trừ khỏi lượng cần.
     */
    private Optional<String> planAndReserve(long orderItemId, List<RecipeLine> recipe, int servings, List<KitchenBatch> lockedBatches) {
        Instant now = clock.now();
        List<StockReservation> existing = reservationRepository.findAllOfOrderItem(orderItemId);
        Map<Long, StockReservation> activeByBatch = new HashMap<>();
        Set<Long> closedBatchIds = new HashSet<>();     // lô đã từng giữ rồi trả/nấu: không giữ lại lô đó (mỗi món-lô một dòng)
        for (StockReservation r : existing) {
            if (r.isActive()) {
                activeByBatch.put(r.getKitchenBatch().getId(), r);
            } else {
                closedBatchIds.add(r.getKitchenBatch().getId());
            }
        }

        List<Take> plan = new ArrayList<>();
        for (RecipeLine line : recipe) {
            BigDecimal needed = line.quantityPerServing().multiply(BigDecimal.valueOf(servings));
            BigDecimal alreadyHeld = activeByBatch.values().stream()
                    .filter(r -> r.getKitchenBatch().getKitchenIngredientTypeId() == line.kitchenIngredientTypeId())
                    .map(StockReservation::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal missing = needed.subtract(alreadyHeld);
            List<KitchenBatch> candidates = lockedBatches.stream()
                    .filter(b -> b.getKitchenIngredientTypeId() == line.kitchenIngredientTypeId())
                    .filter(b -> b.isUsableAt(now) && b.getFreeQuantity().signum() > 0)
                    .filter(b -> !closedBatchIds.contains(b.getId()))
                    .sorted(KitchenStockAllocatorImpl::earliestExpiryFirst)
                    .toList();
            for (KitchenBatch batch : candidates) {
                if (missing.signum() <= 0) {
                    break;
                }
                BigDecimal take = missing.min(batch.getFreeQuantity());
                plan.add(new Take(batch, take));
                missing = missing.subtract(take);
            }
            if (missing.signum() > 0) {
                return Optional.of("Thiếu nguyên liệu: loại bếp " + line.kitchenIngredientTypeId() + " còn thiếu "
                        + missing.stripTrailingZeros().toPlainString());
            }
        }

        for (Take take : plan) {
            take.batch().reserve(take.quantity());
            StockReservation held = activeByBatch.get(take.batch().getId());
            if (held != null) {
                held.addQuantity(take.quantity());
            } else {
                reservationRepository.save(new StockReservation(orderItemId, take.batch(), take.quantity(), now));
            }
        }
        return Optional.empty();
    }

    /** FEFO: hạn dùng sớm trước, lô không có hạn để cuối; cùng hạn thì lô cũ (id nhỏ) trước. */
    private static int earliestExpiryFirst(KitchenBatch a, KitchenBatch b) {
        if (a.getUsableUntil() == null && b.getUsableUntil() != null) {
            return 1;
        }
        if (a.getUsableUntil() != null && b.getUsableUntil() == null) {
            return -1;
        }
        int byExpiry = a.getUsableUntil() == null ? 0 : a.getUsableUntil().compareTo(b.getUsableUntil());
        return byExpiry != 0 ? byExpiry : a.getId().compareTo(b.getId());
    }

    /** Khóa các lô mà món đang giữ (theo thứ tự khóa chung), trả về các lần giữ ACTIVE xếp theo lô. */
    private List<StockReservation> lockBatchesOfActiveReservations(long orderItemId) {
        List<Long> batchIds = reservationRepository.findActiveBatchIdsOfOrderItem(orderItemId);
        if (batchIds.isEmpty()) {
            return List.of();
        }
        kitchenBatchRepository.lockBatchesByIds(batchIds);
        return reservationRepository.findActiveOfOrderItem(orderItemId);
    }
}
