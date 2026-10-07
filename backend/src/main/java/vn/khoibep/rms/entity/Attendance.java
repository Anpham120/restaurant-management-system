package vn.khoibep.rms.entity;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


/** One clock-in, and its clock-out once the person leaves. */
@Entity
@Table(name = "attendance")
@Getter
@Setter
@NoArgsConstructor
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    /** Null for work a manager adds outside the schedule. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_assignment_id")
    private ShiftAssignment shiftAssignment;

    @Column(nullable = false)
    private Instant checkInAt;

    /** Null while the person is still in the shift. */
    private Instant checkOutAt;

    @Column(nullable = false)
    private int lateMinutes;

    @Column(nullable = false)
    private int earlyMinutes;

    private Integer workedMinutes;

    private String editReason;

    private Long editedBy;

    private Instant editedAt;

    public Attendance(Employee employee, ShiftAssignment shiftAssignment, Instant checkInAt) {
        this.employee = employee;
        this.shiftAssignment = shiftAssignment;
        this.checkInAt = checkInAt;
    }

    /**
     * BR-25: late and early are measured against the scheduled shift; worked time is the real time between
     * clock-in and clock-out.
     */
    public void recompute(ZoneId zone) {
        lateMinutes = 0;
        earlyMinutes = 0;
        if (shiftAssignment != null) {
            lateMinutes = minutesAfter(shiftAssignment.startsAt(zone), checkInAt);
            if (checkOutAt != null) {
                earlyMinutes = minutesAfter(checkOutAt, shiftAssignment.endsAt(zone));
            }
        }
        workedMinutes = checkOutAt == null ? null : (int) Duration.between(checkInAt, checkOutAt).toMinutes();
    }

    /** Whole minutes from {@code from} to {@code to}, or 0 if {@code to} is not later. */
    private static int minutesAfter(Instant from, Instant to) {
        return (int) Math.max(0, Duration.between(from, to).toMinutes());
    }
}
