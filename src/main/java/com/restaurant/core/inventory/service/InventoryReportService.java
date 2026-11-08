package com.restaurant.core.inventory.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.restaurant.core.inventory.dto.response.InventoryReportRows.HandoverRow;
import com.restaurant.core.inventory.dto.response.InventoryReportRows.KitchenLotRow;
import com.restaurant.core.inventory.dto.response.InventoryReportRows.KitchenStockRow;
import com.restaurant.core.inventory.dto.response.InventoryReportRows.PreparationYieldRow;
import com.restaurant.core.inventory.dto.response.InventoryReportRows.RawLotRow;
import com.restaurant.core.inventory.dto.response.InventoryReportRows.RawStockRow;
import com.restaurant.core.inventory.dto.response.InventoryReportRows.StockHistoryRow;
import com.restaurant.core.inventory.dto.response.InventoryReportRows.UnusableLotRow;

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
