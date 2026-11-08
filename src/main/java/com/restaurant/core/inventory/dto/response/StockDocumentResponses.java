package com.restaurant.core.inventory.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Chứng từ nhập / xuất / kiểm kê. requestKey do client sinh một lần cho mỗi thao tác "Tạo" (QĐ-ID): gửi lặp cùng khóa
 * và cùng nội dung → trả phiếu cũ; cùng khóa khác nội dung → RC409.
 */
public final class StockDocumentResponses {

    private StockDocumentResponses() {
    }

    // ---------- Phiếu nhập
    public record ReceiptResponse(long id, String status, String supplierName, String note, String requestKey, Instant createdAt,
                                  long createdById, Instant receivedAt, Long confirmedById, Instant confirmedAt,
                                  BigDecimal totalAmount, List<ReceiptLineView> lines) {

        public record Header(long id, String status, String supplierName, String note, String requestKey, Instant createdAt,
                             long createdById, Instant receivedAt, Long confirmedById, Instant confirmedAt) {
        }
    }

    public record ReceiptLineView(long id, long ingredientId, String ingredientName, String baseUnit, BigDecimal inputQuantity,
                                  String inputUnit, BigDecimal conversionFactor, BigDecimal baseQuantity,
                                  BigDecimal unitPricePerInputUnit, BigDecimal lineAmount, String lotCode, LocalDate expiryDate,
                                  Long inventoryBatchId) {
    }

    // ---------- Phiếu xuất
    public record IssueResponse(long id, String status, String note, String requestKey, Instant createdAt, long issuedById,
                                Instant issuedAt, Long receivedById, Instant receivedAt, String cancelReason, Instant cancelledAt,
                                List<IssueLineView> lines) {

        public record Header(long id, String status, String note, String requestKey, Instant createdAt, long issuedById,
                             Instant issuedAt, Long receivedById, Instant receivedAt, String cancelReason, Instant cancelledAt) {
        }
    }

    public record IssueLineView(long id, long inventoryBatchId, long ingredientId, String ingredientName, String lotCode,
                                BigDecimal quantity, String fulfillmentMode, BigDecimal wipRemainingQuantity, Long kitchenBatchId,
                                String baseUnit) {
    }

    // ---------- Phiếu kiểm kê
    public record CountResponse(long id, String status, String note, String requestKey, Instant createdAt, long createdById,
                                Long confirmedById, Instant confirmedAt, List<CountLineView> lines) {

        public record Header(long id, String status, String note, String requestKey, Instant createdAt, long createdById,
                             Long confirmedById, Instant confirmedAt) {
        }
    }

    public record CountLineView(long id, Long rawBatchId, Long wipIssueItemId, Long kitchenBatchId, BigDecimal bookQuantity,
                                BigDecimal actualQuantity, BigDecimal difference, String reason) {
    }
}
