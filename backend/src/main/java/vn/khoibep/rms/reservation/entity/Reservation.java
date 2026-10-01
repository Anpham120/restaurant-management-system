package vn.khoibep.rms.reservation.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import vn.khoibep.rms.common.exception.ApiException;
import vn.khoibep.rms.payment.enums.Confirmation;
import vn.khoibep.rms.reservation.enums.ReservationStatus;
import vn.khoibep.rms.table.entity.DiningTable;

/**
 * A booking (FR-18). Its code is also the transfer content of the deposit. The deposit is held money, not revenue,
 * until it comes off the bill of the order the booking opened (BR-42).
 */
@Entity
@Table(name = "reservation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String guestName;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private Instant reservedAt;

    @Column(nullable = false)
    private int guestCount;

    /** The table planned for the guests; they may still be seated at another one. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id")
    private DiningTable table;

    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status = ReservationStatus.BOOKED;

    /** VND asked for in advance; 0 when no deposit is asked. */
    @Column(nullable = false)
    private long depositAmount;

    private Instant depositPaidAt;

    @Enumerated(EnumType.STRING)
    private Confirmation depositConfirmation;

    private Long depositConfirmedBy;

    /** What came off the bill when the order was paid; null until then. */
    private Long depositApplied;

    /** FR-18.2: the confirmation as it was sent, kept as evidence. */
    private String confirmationText;

    private Instant confirmationSentAt;

    @Column(nullable = false)
    private Long createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    public Reservation(String code, Long createdBy, Instant createdAt) {
        this.code = code;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    /** BR-42: the details of a booking still waiting for its guests; a deposit received keeps its amount. */
    public void describe(String guestName, String phone, Instant reservedAt, int guestCount, DiningTable table,
                         String note, long depositAmount) {
        requireBooked();
        if (isDepositPaid() && depositAmount != this.depositAmount) {
            throw ApiException.conflict("Đã nhận cọc, không đổi số tiền cọc được");
        }
        this.guestName = guestName;
        this.phone = phone;
        this.reservedAt = reservedAt;
        this.guestCount = guestCount;
        this.table = table;
        this.note = note;
        this.depositAmount = depositAmount;
    }

    public boolean isDepositPaid() {
        return depositPaidAt != null;
    }

    /** BR-42: the deposit came in, by the webhook or confirmed by hand. */
    public void depositPaid(Confirmation how, Long confirmedBy, Instant at) {
        requireDepositDue();
        depositConfirmation = how;
        depositConfirmedBy = confirmedBy;
        depositPaidAt = at;
    }

    /** A deposit can be asked for, or confirmed, only while the booking waits for it. */
    public void requireDepositDue() {
        requireBooked();
        if (depositAmount == 0) {
            throw ApiException.conflict("Booking này không có cọc");
        }
        if (isDepositPaid()) {
            throw ApiException.conflict("Đã nhận cọc của booking này");
        }
    }

    /** BR-42: what comes off a bill of this size: the deposit received, at most the bill; fixed once paid. */
    public long depositCredit(long bill) {
        if (!isDepositPaid()) {
            return 0;
        }
        return depositApplied != null ? depositApplied : Math.min(depositAmount, Math.max(0, bill));
    }

    /** BR-42: the bill is paid, so the deposit that came off it is revenue from now on. */
    public void applyDeposit(long bill) {
        if (isDepositPaid() && depositApplied == null) {
            depositApplied = depositCredit(bill);
        }
    }

    public void seat() {
        requireBooked();
        status = ReservationStatus.SEATED;
    }

    /** Cancelled or no-show; a deposit received stays held for a manager to deal with. */
    public void end(ReservationStatus outcome) {
        requireBooked();
        status = outcome;
    }

    public void confirmationSent(String text, Instant at) {
        confirmationText = text;
        confirmationSentAt = at;
    }

    private void requireBooked() {
        if (status != ReservationStatus.BOOKED) {
            throw ApiException.conflict("Booking " + status.label() + ", không đổi được nữa");
        }
    }
}
