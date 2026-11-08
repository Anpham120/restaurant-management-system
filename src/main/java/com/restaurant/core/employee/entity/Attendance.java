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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Bảng attendances: kết quả chấm công của một lần phân ca (PRESENT có giờ vào/ra và số phút công, ABSENT thì không). */
@Entity
@Table(name = "attendances")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shift_assignment_id", nullable = false, updatable = false)
    private ShiftAssignment shiftAssignment;

    @Column(name = "attendance_status", nullable = false, length = 20)
    private String status;

    @Column(name = "actual_start_at")
    private Instant actualStartAt;

    @Column(name = "actual_end_at")
    private Instant actualEndAt;

    @Column(name = "worked_minutes")
    private Integer workedMinutes;

    @Column(length = 255)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by_id")
    private Employee confirmedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    public Attendance(ShiftAssignment shiftAssignment, String note) {
        this.shiftAssignment = shiftAssignment;
        this.note = note;
    }

    /** Ghi (hoặc sửa) kết quả chấm công và người xác nhận. */
    public void record(String status, Instant actualStartAt, Instant actualEndAt, Integer workedMinutes, Employee confirmedBy, Instant now) {
        this.status = status;
        this.actualStartAt = actualStartAt;
        this.actualEndAt = actualEndAt;
        this.workedMinutes = workedMinutes;
        this.confirmedBy = confirmedBy;
        this.confirmedAt = now;
    }

    public Long getShiftAssignmentId() {
        return shiftAssignment.getId();
    }

    public Long getConfirmedById() {
        return confirmedBy == null ? null : confirmedBy.getId();
    }
}
