package com.restaurant.core.employee.entity;

import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Bảng shifts: ca làm mẫu (giờ địa phương). Ca có giờ kết thúc ≤ giờ bắt đầu là ca qua đêm. */
@Entity
@Table(name = "shifts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Shift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shift_name", nullable = false, length = 50)
    private String name;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "break_minutes", nullable = false)
    private int breakMinutes;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    public Shift(String name, LocalTime startTime, LocalTime endTime, int breakMinutes, boolean active) {
        changeDetails(name, startTime, endTime, breakMinutes, active);
    }

    /** Ca đã phân giữ giờ đã chụp, nên sửa ca chỉ ảnh hưởng các lần phân ca sau. */
    public void changeDetails(String name, LocalTime startTime, LocalTime endTime, int breakMinutes, boolean active) {
        this.name = name;
        this.startTime = startTime;
        this.endTime = endTime;
        this.breakMinutes = breakMinutes;
        this.active = active;
    }

    public boolean isOvernight() {
        return !endTime.isAfter(startTime);
    }
}
