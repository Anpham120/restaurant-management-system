package vn.khoibep.rms.dto;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import vn.khoibep.rms.entity.InventoryItem;
import vn.khoibep.rms.enums.MovementType;

public final class InventoryDtos {

    private InventoryDtos() {
    }

    /** @param unitCost cost of one unit in VND, the weighted average of receipts; null before the first (BR-37) */
    public record InventoryItemDto(Long id, String name, String unit, BigDecimal quantity, BigDecimal minQuantity,
                                   boolean lowStock, Long unitCost) {
        public static InventoryItemDto from(InventoryItem i) {
            return new InventoryItemDto(i.getId(), i.getName(), i.getUnit(), i.getQuantity(), i.getMinQuantity(),
                    i.isLowStock(), i.getUnitCost());
        }
    }

    /** New items start at zero; stock comes in through an IN movement (BR-19). */
    public record InventoryItemRequest(@NotBlank @Size(max = 100) String name,
                                       @NotBlank @Size(max = 20) String unit,
                                       @NotNull @DecimalMin("0") @Digits(integer = 9, fraction = 3)
                                       BigDecimal minQuantity) {
    }

    /** For ADJUST, quantity is the counted stock; for IN and OUT it is the amount moved. */
    public record MovementRequest(@NotNull MovementType type,
                                  @NotNull @DecimalMin("0") @Digits(integer = 9, fraction = 3) BigDecimal quantity,
                                  @Size(max = 300) String note) {
    }

    public record MovementDto(Long id, MovementType type, BigDecimal quantityChange, BigDecimal quantityAfter,
                              String note, String createdByName, Instant createdAt) {
    }
}
