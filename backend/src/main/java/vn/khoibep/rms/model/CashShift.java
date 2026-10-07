package vn.khoibep.rms.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** A shift of the cash drawer (BR-39). Open until it is closed with the count; never changed after that. */
@Entity
@Table(name = "cash_shift")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CashShift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "opened_by")
    private Employee openedBy;

    @Column(nullable = false)
    private Instant openedAt;

    /** Cash in the drawer when the shift began, in VND. */
    @Column(nullable = false)
    private long openingFloat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by")
    private Employee closedBy;

    private Instant closedAt;

    /** What should have been in the drawer at closing; worked out from the cash while the shift is open. */
    private Long expectedCash;

    private Long countedCash;

    /** Why the count differs from what was expected. */
    private String closeNote;

    public CashShift(Employee openedBy, long openingFloat, Instant openedAt) {
        this.openedBy = openedBy;
        this.openingFloat = openingFloat;
        this.openedAt = openedAt;
    }

    public boolean isOpen() {
        return closedAt == null;
    }

    /** BR-39: the count is recorded beside what was expected; a difference needs a reason. */
    public void close(long expected, long counted, String note, Employee by, Instant at) {
        if (counted != expected && note == null) {
            throw ApiException.badRequest("Tiền đếm lệch với dự kiến: cần ghi lý do");
        }
        expectedCash = expected;
        countedCash = counted;
        closeNote = note;
        closedBy = by;
        closedAt = at;
    }
}
