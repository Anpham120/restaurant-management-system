package com.restaurant.core.inventory.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Dòng kết quả của các hàm rpt_* (docs/10-bao-cao-v4.1.sql) và danh sách lô để chọn khi lập chứng từ. */
public final class InventoryReportDtos {

    private InventoryReportDtos() {
    }

    public record RawStockRow(long ingredientId, String ingredientName, String baseUnit, BigDecimal usableQuantity,
                              BigDecimal unusableQuantity, BigDecimal expiredQuantity, BigDecimal minQuantity, boolean belowMinimum) {
    }

    public record HandoverRow(String stage, long ingredientId, String ingredientName, long stockIssueId, long stockIssueItemId,
                              long inventoryBatchId, BigDecimal quantity) {
    }

    public record KitchenStockRow(long kitchenIngredientTypeId, String typeName, String baseUnit, BigDecimal usableOnHand,
                                  BigDecimal reservedQuantity, BigDecimal availableQuantity, BigDecimal unusableOrExpired,
                                  BigDecimal minimumQuantity) {
    }

    public record UnusableLotRow(String location, long batchId, String itemName, BigDecimal quantity, String reason, Instant expires) {
    }

    public record StockHistoryRow(long movementId, Instant createdAt, LocalDate businessDate, String category, String movementType,
                                  String location, long ingredientId, String ingredientName, BigDecimal quantityDelta,
                                  String reference, String reason) {
    }

    public record PreparationYieldRow(long preparationId, LocalDate businessDate, Instant preparedAt, long ingredientId,
                                      String ingredientName, long inventoryBatchId, String lotCode, long preparedById,
                                      String preparedBy, BigDecimal inputQuantity, BigDecimal outputQuantity, BigDecimal lossQuantity,
                                      BigDecimal yieldRatio, BigDecimal lossRatio, String lossReason) {
    }

    public record RawLotRow(long inventoryBatchId, long ingredientId, String ingredientName, String lotCode, LocalDate expiryDate,
                            Instant receivedAt, BigDecimal quantityOnHand, boolean usable) {
    }

    /** directIssueItemId: dòng xuất DIRECT sinh ra lô (null nếu lô từ sơ chế) — cần khi hoàn trả về kho. */
    public record KitchenLotRow(long kitchenBatchId, long kitchenIngredientTypeId, String typeName, BigDecimal quantityOnHand,
                                BigDecimal reservedQuantity, Instant usableUntil, boolean usable, Long directIssueItemId) {
    }
}
