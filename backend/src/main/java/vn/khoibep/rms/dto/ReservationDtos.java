package vn.khoibep.rms.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import vn.khoibep.rms.enums.Confirmation;
import vn.khoibep.rms.enums.ReservationStatus;
import vn.khoibep.rms.model.Reservation;

/** Bookings and their deposits (FR-18). Amounts in VND. */
public final class ReservationDtos {

    private ReservationDtos() {
    }

    /** @param depositAmount 0 when no deposit is asked */
    public record ReservationRequest(@NotBlank @Size(max = 100) String guestName,
                                     @NotBlank @Size(max = 20) String phone,
                                     @NotNull Instant reservedAt,
                                     @NotNull @Min(1) @Max(200) Integer guestCount,
                                     Long tableId,
                                     @Size(max = 300) String note,
                                     @NotNull @Min(0) @Max(1_000_000_000) Long depositAmount) {
    }

    /** @param tableId where the guests sit; the planned table when null */
    public record SeatRequest(Long tableId) {
    }

    /** @param orderId the order the booking opened, once the guests are seated */
    public record ReservationDto(Long id, String code, String guestName, String phone, Instant reservedAt,
                                 int guestCount, Long tableId, String tableName, String note,
                                 ReservationStatus status, long depositAmount, Instant depositPaidAt,
                                 Confirmation depositConfirmation, Long depositApplied, Instant confirmationSentAt,
                                 Long orderId) {
        public static ReservationDto from(Reservation r, Long orderId) {
            return new ReservationDto(r.getId(), r.getCode(), r.getGuestName(), r.getPhone(), r.getReservedAt(),
                    r.getGuestCount(), r.getTable() == null ? null : r.getTable().getId(),
                    r.getTable() == null ? null : r.getTable().getName(), r.getNote(), r.getStatus(),
                    r.getDepositAmount(), r.getDepositPaidAt(), r.getDepositConfirmation(), r.getDepositApplied(),
                    r.getConfirmationSentAt(), orderId);
        }
    }

    /** @param depositsHeld every deposit received and not yet taken off a bill, whatever its day */
    public record ReservationDayDto(LocalDate date, long depositsHeld, List<ReservationDto> reservations) {
    }

    /** @param sentAt when the confirmation was last marked sent; null before */
    public record ConfirmationDto(String text, Instant sentAt) {
    }

    /** VietQR for a deposit: the booking code is the transfer content (BR-42). */
    public record DepositInstruction(Long reservationId, long amount, String reference, String qrImageUrl,
                                     String bankCode, String bankAccountNo, String bankAccountName) {
    }
}
