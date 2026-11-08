package com.restaurant.core.inventory.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Sơ chế, hoàn trả về kho, hủy hàng (hư hỏng/hết hạn). Phiếu nháp ghi trực tiếp; xác nhận bằng hàm rc_*. */
public final class KitchenStockResponses {

    private KitchenStockResponses() {
    }

    // ---------- Sơ chế: đầu vào từ một dòng WIP (phiếu xuất PREPARE đã nhận) → nhiều đầu ra PREPARED
    /** lossQuantity = đầu vào − tổng đầu ra (giá trị suy ra, không lưu). */
    public record PreparationResponse(long id, String status, long stockIssueItemId, BigDecimal inputQuantity,
                                      BigDecimal outputQuantity, BigDecimal lossQuantity, String lossReason, long preparedById,
                                      Instant preparedAt, String requestKey, List<OutputView> outputs) {

        public record Header(long id, String status, long stockIssueItemId, BigDecimal inputQuantity, String lossReason,
                             long preparedById, Instant preparedAt, String requestKey) {
        }
    }

    public record OutputView(long id, long kitchenIngredientTypeId, String typeName, BigDecimal outputQuantity, Instant usableUntil,
                             Long kitchenBatchId) {
    }

    // ---------- Hoàn trả về kho RAW
    public record ReturnResponse(long id, String status, String reason, long requestedById, Long confirmedById, Instant createdAt,
                                 Instant confirmedAt, String requestKey, List<ReturnLineView> lines) {

        public record Header(long id, String status, String reason, long requestedById, Long confirmedById, Instant createdAt,
                             Instant confirmedAt, String requestKey) {
        }
    }

    public record ReturnLineView(long id, long sourceIssueItemId, Long directKitchenBatchId, BigDecimal quantity) {
    }

    // ---------- Hủy hàng (WASTE)
    public record AdjustmentResponse(long id, String status, String adjustmentType, String reason, long createdById,
                                     Long confirmedById, Instant createdAt, Instant confirmedAt, String requestKey,
                                     List<AdjustmentLineView> lines) {

        public record Header(long id, String status, String adjustmentType, String reason, long createdById, Long confirmedById,
                             Instant createdAt, Instant confirmedAt, String requestKey) {
        }
    }

    public record AdjustmentLineView(long id, Long rawBatchId, Long wipIssueItemId, Long kitchenBatchId, BigDecimal quantityDelta,
                                     String note) {
    }

    public record MarkUnusableResponse(String kind, long batchId, int affectedOrderItems) {
    }
}
