package vn.khoibep.rms.model;

import java.time.Instant;
import java.time.LocalDate;
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


/** One employee scheduled for one shift on one day. */
@Entity
@Table(name = "shift_assignment")
@Getter
@NoArgsConstructor
public class ShiftAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_shift_id")
    private WorkShift workShift;

    @Column(nullable = false)
    private LocalDate workDate;

    @Column(nullable = false)
    private Long createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public ShiftAssignment(Employee employee, WorkShift workShift, LocalDate workDate, Long createdBy) {
        this.employee = employee;
        this.workShift = workShift;
        this.workDate = workDate;
        this.createdBy = createdBy;
    }

    public Instant startsAt(ZoneId zone) {
        return workDate.atTime(workShift.getStartTime()).atZone(zone).toInstant();
    }

    public Instant endsAt(ZoneId zone) {
        return workDate.atTime(workShift.getEndTime()).atZone(zone).toInstant();
    }
}
