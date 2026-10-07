package vn.khoibep.rms.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class TableDtos {

    private TableDtos() {
    }

    /**
     * Table with its live state for the floor plan (FR-04.4).
     *
     * @param status       AVAILABLE or OCCUPIED
     * @param pendingCount guest dishes waiting for staff confirmation
     * @param readyCount   dishes cooked and waiting to be served
     */
    /** @param groupLabel the tables of the open order, "B05 + B06", when it holds more than this one (FR-04.5) */
    public record TableDto(Long id, String name, String area, int seats, String qrToken, String qrUrl,
                           String status, Long openOrderId, Integer guestCount, int pendingCount, int readyCount,
                           String groupLabel) {
    }

    public record TableRequest(@NotBlank @Size(max = 50) String name,
                               @Size(max = 50) String area,
                               @NotNull @Min(1) @Max(50) Integer seats) {
    }
}
