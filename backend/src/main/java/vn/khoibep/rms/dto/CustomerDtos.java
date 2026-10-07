package vn.khoibep.rms.dto;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import vn.khoibep.rms.enums.ConsentChannel;
import vn.khoibep.rms.enums.ReservationStatus;

/** Guests known by their phone number (FR-19). Amounts in VND. */
public final class CustomerDtos {

    private CustomerDtos() {
    }

    /** @param name kept when the guest is already known and this is blank */
    public record AttachRequest(@NotBlank @Size(max = 20) String phone, @Size(max = 100) String name) {
    }

    public record CustomerRequest(@Size(max = 100) String name, @Size(max = 300) String note) {
    }

    public record ConsentRequest(@NotNull ConsentChannel channel, @NotBlank @Size(max = 100) String source) {
    }

    /**
     * @param visits     paid orders of the guest
     * @param spent      what those orders brought in: payments and deposits taken off them
     * @param mayContact BR-44: agreed, and not refused since
     */
    public record CustomerDto(Long id, String phone, String name, String note, ConsentChannel consentChannel,
                              Instant consentAt, String consentSource, Instant optedOutAt, boolean mayContact,
                              long visits, long spent, Instant lastVisitAt) {
    }

    /** @param paid payments and the deposit taken off the bill */
    public record VisitDto(Long orderId, Instant closedAt, String tableName, long paid) {
    }

    public record BookingDto(Long id, String code, Instant reservedAt, int guestCount, ReservationStatus status) {
    }

    /** FR-19.3: the guest with the latest visits and bookings. */
    public record CustomerDetailDto(CustomerDto customer, List<VisitDto> visits, List<BookingDto> bookings) {
    }
}
