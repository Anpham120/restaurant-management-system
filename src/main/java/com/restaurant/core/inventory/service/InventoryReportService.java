package com.restaurant.core.inventory.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.restaurant.core.common.exception.ApiException;
import com.restaurant.core.inventory.dto.InventoryReportDtos.HandoverRow;
import com.restaurant.core.inventory.dto.InventoryReportDtos.KitchenLotRow;
import com.restaurant.core.inventory.dto.InventoryReportDtos.KitchenStockRow;
import com.restaurant.core.inventory.dto.InventoryReportDtos.PreparationYieldRow;
import com.restaurant.core.inventory.dto.InventoryReportDtos.RawLotRow;
import com.restaurant.core.inventory.dto.InventoryReportDtos.RawStockRow;
import com.restaurant.core.inventory.dto.InventoryReportDtos.StockHistoryRow;
import com.restaurant.core.inventory.dto.InventoryReportDtos.UnusableLotRow;
import com.restaurant.core.inventory.repository.InventoryReportRepository;

/** Báo cáo tồn kho, lịch sử, hiệu suất sơ chế. Owner: Đỗ Tuấn Anh. */
@Service
public class InventoryReportService {

    private final InventoryReportRepository repo;

    public InventoryReportService(InventoryReportRepository repo) {
        this.repo = repo;
    }

    public List<RawStockRow> rawStock() {
        return repo.rawStock();
    }

    public List<HandoverRow> handoverAndWip() {
        return repo.handoverAndWip();
    }

    public List<KitchenStockRow> kitchenStock() {
        return repo.kitchenStock();
    }

    public List<UnusableLotRow> unusableLots() {
        return repo.unusableLots();
    }

    public List<StockHistoryRow> stockHistory(Instant from, Instant to, Long ingredientId) {
        if (!from.isBefore(to)) {
            throw badRange();
        }
        return repo.stockHistory(from, to, ingredientId);
    }

    public List<PreparationYieldRow> preparationYield(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw badRange();
        }
        return repo.preparationYield(from, to);
    }

    public List<RawLotRow> rawLots(Long ingredientId) {
        return repo.rawLots(ingredientId);
    }

    public List<KitchenLotRow> kitchenLots(Long typeId) {
        return repo.kitchenLots(typeId);
    }

    private static ApiException badRange() {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Khoảng thời gian không hợp lệ: from phải trước to");
    }
}
