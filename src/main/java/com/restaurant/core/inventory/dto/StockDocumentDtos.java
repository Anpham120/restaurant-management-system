package com.restaurant.core.inventory.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Chứng từ nhập / xuất / kiểm kê. requestKey do client sinh một lần cho mỗi thao tác "Tạo" (QĐ-ID): gửi lặp cùng khóa
 * và cùng nội dung → trả phiếu cũ; cùng khóa khác nội dung → RC409.
 */
public final class StockDocumentDtos {

    private StockDocumentDtos() {
    }

    // ---------- Phiếu nhập
    public record ReceiptRequest(@NotBlank @Size(max = 64) String requestKey, @Size(max = 100) String supplierName,
                                 @Size(max = 255) String note, @NotEmpty @Size(max = 100) List<@Valid @NotNull ReceiptLine> items) {
    }

    /** Số lượng nhập theo inputUnit; tồn = inputQuantity × conversionFactor theo base_unit của nguyên liệu. */
    public record ReceiptLine(@NotNull @Positive Long ingredientId,
                              @NotNull @Positive @Digits(integer = 9, fraction = 3) BigDecimal inputQuantity,
                              @NotBlank @Size(max = 20) String inputUnit,
                              @NotNull @Positive @Digits(integer = 9, fraction = 3) BigDecimal conversionFactor,
                              @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal unitPricePerInputUnit,
                              @Size(max = 50) String lotCode, LocalDate expiryDate) {
    }

    public record ConfirmReceiptRequest(Instant receivedAt) {
    }

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
    public record IssueRequest(@NotBlank @Size(max = 64) String requestKey, @Size(max = 255) String note,
                               @NotEmpty @Size(max = 100) List<@Valid @NotNull IssueLine> items) {
    }

    /** fulfillmentMode: DIRECT → lô bếp dùng thẳng; PREPARE → WIP chờ sơ chế. Lô phải theo FEFO (DB kiểm). */
    public record IssueLine(@NotNull @Positive Long inventoryBatchId,
                            @NotNull @Positive @Digits(integer = 9, fraction = 3) BigDecimal quantity,
                            @NotNull @Pattern(regexp = "DIRECT|PREPARE") String fulfillmentMode) {
    }

    public record IssueResponse(long id, String status, String note, String requestKey, Instant createdAt, long issuedById,
                                Instant issuedAt, Long receivedById, Instant receivedAt, String cancelReason, Instant cancelledAt,
                                List<IssueLineView> lines) {

        public record Header(long id, String status, String note, String requestKey, Instant createdAt, long issuedById,
                             Instant issuedAt, Long receivedById, Instant receivedAt, String cancelReason, Instant cancelledAt) {
        }
    }

    public record IssueLineView(long id, long inventoryBatchId, long ingredientId, String ingredientName, String lotCode,
                                BigDecimal quantity, String fulfillmentMode, BigDecimal wipRemainingQuantity, Long kitchenBatchId) {
    }

    // ---------- Phiếu kiểm kê
    public record CountRequest(@NotBlank @Size(max = 64) String requestKey, @Size(max = 255) String note,
                               @NotEmpty @Size(max = 200) List<@Valid @NotNull CountLine> items) {
    }

    /** Đúng một nguồn: lô RAW, dòng WIP hoặc lô bếp. Số sổ được DB chụp lúc tạo phiếu. */
    public record CountLine(@Positive Long rawBatchId, @Positive Long wipIssueItemId, @Positive Long kitchenBatchId,
                            @NotNull @PositiveOrZero @Digits(integer = 9, fraction = 3) BigDecimal actualQuantity,
                            @Size(max = 255) String reason) {
    }

    public record CountResponse(long id, String status, String note, String requestKey, Instant createdAt, long createdById,
                                Long confirmedById, Instant confirmedAt, List<CountLineView> lines) {

        public record Header(long id, String status, String note, String requestKey, Instant createdAt, long createdById,
                             Long confirmedById, Instant confirmedAt) {
        }
    }

    public record CountLineView(long id, Long rawBatchId, Long wipIssueItemId, Long kitchenBatchId, BigDecimal bookQuantity,
                                BigDecimal actualQuantity, BigDecimal difference, String reason) {
    }

    public record ReasonRequest(@NotBlank @Size(max = 200) String reason) {
    }
}
