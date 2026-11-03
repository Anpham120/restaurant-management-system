package com.restaurant.core.employee.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Bảng shift_assignments: phân một nhân viên vào một ca của một ngày. Giờ ca được CHỤP lại lúc phân (scheduled_*),
 * nên sửa ca mẫu sau đó không làm đổi lịch đã xếp.
 */
@Entity
@Table(name = "shift_assignments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShiftAssignment {

    public static final String SCHEDULED = "SCHEDULED";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private Long employeeId;

    @Column(name = "shift_id", nullable = false, updatable = false)
    private Long shiftId;

    @Column(name = "work_date", nullable = false, updatable = false)
    private LocalDate workDate;

    @Column(name = "scheduled_start_at", nullable = false, updatable = false)
    private Instant scheduledStartAt;

    @Column(name = "scheduled_end_at", nullable = false, updatable = false)
    private Instant scheduledEndAt;

    @Column(name = "scheduled_break_minutes", nullable = false, updatable = false)
    private int scheduledBreakMinutes;

    @Column(nullable = false, length = 20)
    private String status = SCHEDULED;

    @Column(length = 255)
    private String note;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    @Column(name = "cancelled_by_id")
    private Long cancelledById;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    /** Chụp giờ ca vào ngày làm theo múi giờ của quán; ca qua đêm kết thúc vào ngày hôm sau. */
    public static ShiftAssignment schedule(long employeeId, Shift shift, LocalDate workDate, String note, ZoneId zone) {
        ShiftAssignment a = new ShiftAssignment();
        a.employeeId = employeeId;
        a.shiftId = shift.getId();
        a.workDate = workDate;
        a.scheduledStartAt = workDate.atTime(shift.getStartTime()).atZone(zone).toInstant();
        a.scheduledEndAt = workDate.plusDays(shift.isOvernight() ? 1 : 0).atTime(shift.getEndTime()).atZone(zone).toInstant();
        a.scheduledBreakMinutes = shift.getBreakMinutes();
        a.note = note;
        return a;
    }

    public boolean isScheduled() {
        return SCHEDULED.equals(status);
    }

    public boolean isCancelled() {
        return CANCELLED.equals(status);
    }

    public void markCompleted() {
        status = COMPLETED;
    }

    public void cancel(String reason, long actorId, Instant now) {
        status = CANCELLED;
        cancelReason = reason;
        cancelledById = actorId;
        cancelledAt = now;
    }
}
