package com.restaurant.core.inventory.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.restaurant.core.inventory.dto.InventoryReportDtos.HandoverRow;
import com.restaurant.core.inventory.dto.InventoryReportDtos.KitchenLotRow;
import com.restaurant.core.inventory.dto.InventoryReportDtos.KitchenStockRow;
import com.restaurant.core.inventory.dto.InventoryReportDtos.PreparationYieldRow;
import com.restaurant.core.inventory.dto.InventoryReportDtos.RawLotRow;
import com.restaurant.core.inventory.dto.InventoryReportDtos.RawStockRow;
import com.restaurant.core.inventory.dto.InventoryReportDtos.StockHistoryRow;
import com.restaurant.core.inventory.dto.InventoryReportDtos.UnusableLotRow;

/** Báo cáo tồn kho, lịch sử, hiệu suất sơ chế. Owner: Đỗ Tuấn Anh. */
public interface InventoryReportService {

    List<RawStockRow> getRawStock();

    List<HandoverRow> getHandoverAndWip();

    List<KitchenStockRow> getKitchenStock();

    List<UnusableLotRow> getUnusableLots();

    List<StockHistoryRow> getStockHistory(Instant from, Instant to, Long ingredientId);

    List<PreparationYieldRow> getPreparationYield(LocalDate from, LocalDate to);

    List<RawLotRow> getRawLots(Long ingredientId);

    List<KitchenLotRow> getKitchenLots(Long typeId);
}
