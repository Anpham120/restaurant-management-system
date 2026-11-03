package com.restaurant.core.inventory.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.restaurant.core.common.exception.ApiException;
import com.restaurant.core.inventory.entity.MovementType;
import com.restaurant.core.common.time.BusinessDateProvider;
import com.restaurant.core.inventory.dto.InventoryReportDtos.HandoverRow;
import com.restaurant.core.inventory.dto.InventoryReportDtos.KitchenLotRow;
import com.restaurant.core.inventory.dto.InventoryReportDtos.KitchenStockRow;
import com.restaurant.core.inventory.dto.InventoryReportDtos.PreparationYieldRow;
import com.restaurant.core.inventory.dto.InventoryReportDtos.RawLotRow;
import com.restaurant.core.inventory.dto.InventoryReportDtos.RawStockRow;
import com.restaurant.core.inventory.dto.InventoryReportDtos.StockHistoryRow;
import com.restaurant.core.inventory.dto.InventoryReportDtos.UnusableLotRow;
import com.restaurant.core.inventory.repository.InventoryReportRepository;
import com.restaurant.core.inventory.service.InventoryReportService;

@Service
public class InventoryReportServiceImpl implements InventoryReportService {

    private final InventoryReportRepository repo;
    private final BusinessDateProvider clock;

    public InventoryReportServiceImpl(InventoryReportRepository repo, BusinessDateProvider clock) {
        this.repo = repo;
        this.clock = clock;
    }

    @Override
    public List<RawStockRow> getRawStock() {
        return repo.findRawStock(clock.today()).stream()
                .map(r -> new RawStockRow(r.ingredientId(), r.ingredientName(), r.baseUnit(), r.usableQuantity(), r.unusableQuantity(),
                        r.expiredQuantity(), r.minQuantity(), r.usableQuantity().compareTo(r.minQuantity()) < 0))
                .toList();
    }

    @Override
    public List<HandoverRow> getHandoverAndWip() {
        return repo.findHandoverAndWip();
    }

    @Override
    public List<KitchenStockRow> getKitchenStock() {
        return repo.findKitchenStock(clock.now());
    }

    @Override
    public List<UnusableLotRow> getUnusableLots() {
        List<UnusableLotRow> lots = new ArrayList<>();
        for (var raw : repo.findRawUnusableLots(clock.today())) {
            Instant expires = raw.expiryDate() == null ? null
                    : raw.expiryDate().plusDays(1).atStartOfDay(BusinessDateProvider.RESTAURANT_ZONE).toInstant();
            lots.add(new UnusableLotRow("RAW", raw.batchId(), raw.itemName(), raw.quantity(),
                    raw.usable() ? "Quá hạn" : raw.unusableReason(), expires));
        }
        for (var kitchen : repo.findKitchenUnusableLots(clock.now())) {
            lots.add(new UnusableLotRow("KITCHEN", kitchen.batchId(), kitchen.itemName(), kitchen.quantity(),
                    kitchen.usable() ? "Hết hạn dùng tại bếp" : kitchen.unusableReason(), kitchen.usableUntil()));
        }
        return lots;
    }

    @Override
    public List<StockHistoryRow> getStockHistory(Instant from, Instant to, Long ingredientId) {
        if (!from.isBefore(to)) {
            throw badRange();
        }
        return repo.findMovements(from, to, ingredientId).stream()
                .map(m -> new StockHistoryRow(m.movementId(), m.createdAt(), BusinessDateProvider.businessDateOf(m.createdAt()),
                        MovementType.valueOf(m.movementType()).reportCategory(), m.movementType(), m.location(), m.ingredientId(),
                        m.ingredientName(), m.quantityDelta(), m.reference(), m.reason(), m.baseUnit()))
                .toList();
    }

    @Override
    public List<PreparationYieldRow> getPreparationYield(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw badRange();
        }
        return repo.findConfirmedPreparations(BusinessDateProvider.startOf(from), BusinessDateProvider.startOf(to.plusDays(1))).stream()
                .map(InventoryReportServiceImpl::toYieldRow)
                .toList();
    }

    @Override
    public List<RawLotRow> getRawLots(Long ingredientId) {
        return repo.rawLots(ingredientId, clock.today());
    }

    @Override
    public List<KitchenLotRow> getKitchenLots(Long typeId) {
        return repo.kitchenLots(typeId);
    }

    private static ApiException badRange() {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Khoảng thời gian không hợp lệ: from phải trước to");
    }

    /** Hao hụt sơ chế = đầu vào − đầu ra; tỷ lệ thu hồi / hao hụt làm tròn 4 chữ số. */
    private static PreparationYieldRow toYieldRow(InventoryReportRepository.PreparationRow p) {
        BigDecimal loss = p.inputQuantity().subtract(p.outputQuantity());
        return new PreparationYieldRow(p.preparationId(), BusinessDateProvider.businessDateOf(p.preparedAt()), p.preparedAt(),
                p.ingredientId(), p.ingredientName(), p.inventoryBatchId(), p.lotCode(), p.preparedById(), p.preparedBy(),
                p.inputQuantity(), p.outputQuantity(), loss,
                p.outputQuantity().divide(p.inputQuantity(), 4, RoundingMode.HALF_UP),
                loss.divide(p.inputQuantity(), 4, RoundingMode.HALF_UP), p.lossReason(), p.baseUnit());
    }
}
