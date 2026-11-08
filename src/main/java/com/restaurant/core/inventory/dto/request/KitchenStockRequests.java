package com.restaurant.core.inventory.dto.request;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Sơ chế, hoàn trả về kho, hủy hàng (hư hỏng/hết hạn). Phiếu nháp ghi trực tiếp; xác nhận bằng hàm rc_*. */
public final class KitchenStockRequests {

    private KitchenStockRequests() {
    }

    // ---------- Sơ chế: đầu vào từ một dòng WIP (phiếu xuất PREPARE đã nhận) → nhiều đầu ra PREPARED
    public record PreparationRequest(@NotBlank @Size(max = 64) String requestKey, @NotNull @Positive Long stockIssueItemId,
                                     @NotNull @Positive @Digits(integer = 9, fraction = 3) BigDecimal inputQuantity,
                                     @Size(max = 255) String lossReason,
                                     @NotEmpty @Size(max = 20) List<@Valid @NotNull PreparationOutput> outputs) {
    }

    public record PreparationOutput(@NotNull @Positive Long kitchenIngredientTypeId,
                                    @NotNull @Positive @Digits(integer = 9, fraction = 3) BigDecimal outputQuantity,
                                    Instant usableUntil) {
    }

    // ---------- Hoàn trả về kho RAW
    public record ReturnRequest(@NotBlank @Size(max = 64) String requestKey, @NotBlank @Size(max = 255) String reason,
                                @NotEmpty @Size(max = 100) List<@Valid @NotNull ReturnLine> items) {
    }

    /** Hàng WIP: chỉ sourceIssueItemId. Hàng DIRECT: thêm directKitchenBatchId (lô bếp sinh từ đúng dòng xuất đó). */
    public record ReturnLine(@NotNull @Positive Long sourceIssueItemId, @Positive Long directKitchenBatchId,
                             @NotNull @Positive @Digits(integer = 9, fraction = 3) BigDecimal quantity) {
    }

    // ---------- Hủy hàng (WASTE)
    public record AdjustmentRequest(@NotBlank @Size(max = 64) String requestKey,
                                    @NotNull @Pattern(regexp = "DAMAGE|EXPIRED|QUALITY|OTHER") String adjustmentType,
                                    @NotBlank @Size(max = 255) String reason,
                                    @NotEmpty @Size(max = 100) List<@Valid @NotNull AdjustmentLine> items) {
    }

    /** quantity là lượng hủy (số dương); DB lưu quantity_delta âm. Đúng một nguồn. */
    public record AdjustmentLine(@Positive Long rawBatchId, @Positive Long wipIssueItemId, @Positive Long kitchenBatchId,
                                 @NotNull @Positive @Digits(integer = 9, fraction = 3) BigDecimal quantity,
                                 @Size(max = 255) String note) {
    }
}
