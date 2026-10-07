package vn.khoibep.rms.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import vn.khoibep.rms.entity.GoodsReceipt;
import vn.khoibep.rms.entity.ReceiptLine;
import vn.khoibep.rms.entity.Supplier;

/** Suppliers and goods receipts with prices (FR-09.5 → FR-09.7). */
public final class PurchaseDtos {

    private PurchaseDtos() {
    }

    /** @param active null leaves it as it is; false stops the supplier from being chosen (BR-37) */
    public record SupplierRequest(@NotBlank @Size(max = 150) String name,
                                  @Size(max = 20) String phone,
                                  @Size(max = 300) String address,
                                  @Size(max = 20) String taxCode,
                                  @Size(max = 300) String note,
                                  Boolean active) {
    }

    public record SupplierDto(Long id, String name, String phone, String address, String taxCode, String note,
                              boolean active) {
        public static SupplierDto from(Supplier s) {
            return new SupplierDto(s.getId(), s.getName(), s.getPhone(), s.getAddress(), s.getTaxCode(), s.getNote(),
                    s.isActive());
        }
    }

    /** BR-37: more than zero of the ingredient's unit, at a price in VND for one unit. */
    public record ReceiptLineRequest(@NotNull Long inventoryItemId,
                                     @NotNull @DecimalMin(value = "0", inclusive = false)
                                     @Digits(integer = 9, fraction = 3) BigDecimal quantity,
                                     @NotNull @Min(0) @Max(1_000_000_000) Long unitPrice) {
    }

    public record GoodsReceiptRequest(@NotNull Long supplierId,
                                      @Size(max = 300) String note,
                                      @NotEmpty @Size(max = 50) List<@Valid ReceiptLineRequest> lines) {
    }

    public record ReceiptLineDto(Long id, Long inventoryItemId, String itemName, String unit, BigDecimal quantity,
                                 long unitPrice, long lineTotal) {
        public static ReceiptLineDto from(ReceiptLine l) {
            return new ReceiptLineDto(l.getId(), l.getItem().getId(), l.getItem().getName(), l.getItem().getUnit(),
                    l.getQuantity(), l.getUnitPrice(), l.lineTotal());
        }
    }

    public record GoodsReceiptDto(Long id, Long supplierId, String supplierName, String note, String createdByName,
                                  Instant createdAt, long total, List<ReceiptLineDto> lines) {
        public static GoodsReceiptDto from(GoodsReceipt r) {
            return new GoodsReceiptDto(r.getId(), r.getSupplier().getId(), r.getSupplier().getName(), r.getNote(),
                    r.getCreatedBy().getFullName(), r.getCreatedAt(), r.total(),
                    r.getLines().stream().map(ReceiptLineDto::from).toList());
        }
    }
}
