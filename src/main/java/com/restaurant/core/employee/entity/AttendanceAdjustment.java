package com.restaurant.core.employee.entity;

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

/** Bảng attendance_adjustments: nhật ký chỉ-thêm mỗi lần sửa chấm công (giá trị cũ, giá trị mới, lý do, người sửa). */
@Entity
@Table(name = "attendance_adjustments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attendance_id", nullable = false, updatable = false)
    private Attendance attendance;

    @Column(name = "old_status", nullable = false, length = 20, updatable = false)
    private String oldStatus;

    @Column(name = "new_status", nullable = false, length = 20, updatable = false)
    private String newStatus;

    @Column(name = "old_start_at", updatable = false)
    private Instant oldStartAt;

    @Column(name = "new_start_at", updatable = false)
    private Instant newStartAt;

    @Column(name = "old_end_at", updatable = false)
    private Instant oldEndAt;

    @Column(name = "new_end_at", updatable = false)
    private Instant newEndAt;

    @Column(name = "old_worked_minutes", updatable = false)
    private Integer oldWorkedMinutes;

    @Column(name = "new_worked_minutes", updatable = false)
    private Integer newWorkedMinutes;

    @Column(nullable = false, length = 255, updatable = false)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "adjusted_by_id", nullable = false, updatable = false)
    private Employee adjustedBy;

    @Column(name = "adjusted_at", nullable = false, updatable = false)
    private Instant adjustedAt;

    /** Chụp giá trị hiện tại của bản chấm công TRƯỚC khi sửa. */
    public static AttendanceAdjustment before(Attendance current, String newStatus, Instant newStartAt, Instant newEndAt,
                                              Integer newWorkedMinutes, String reason, Employee adjustedBy, Instant now) {
        AttendanceAdjustment a = new AttendanceAdjustment();
        a.attendance = current;
        a.oldStatus = current.getStatus();
        a.oldStartAt = current.getActualStartAt();
        a.oldEndAt = current.getActualEndAt();
        a.oldWorkedMinutes = current.getWorkedMinutes();
        a.newStatus = newStatus;
        a.newStartAt = newStartAt;
        a.newEndAt = newEndAt;
        a.newWorkedMinutes = newWorkedMinutes;
        a.reason = reason;
        a.adjustedBy = adjustedBy;
        a.adjustedAt = now;
        return a;
    }

    public Long getAttendanceId() {
        return attendance.getId();
    }

    public Long getAdjustedById() {
        return adjustedBy.getId();
    }
}
