package com.restaurant.core.employee.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Ca làm, phân ca, chấm công (1.3–1.4). Giờ ca là giờ Việt Nam; endTime ≤ startTime nghĩa là ca qua đêm. */
public final class ShiftDtos {

    private ShiftDtos() {
    }

    public record ShiftRequest(@NotBlank @Size(max = 50) String name, @NotNull LocalTime startTime, @NotNull LocalTime endTime,
                               @PositiveOrZero @Max(600) Integer breakMinutes, Boolean active) {
    }

    public record ShiftResponse(long id, String name, LocalTime startTime, LocalTime endTime, int breakMinutes, boolean active) {
    }

    public record AssignShiftRequest(@NotNull @Positive Long employeeId, @NotNull @Positive Long shiftId, @NotNull LocalDate workDate,
                                     @Size(max = 255) String note) {
    }

    public record ShiftAssignmentResponse(long id, long employeeId, String employeeName, long shiftId, String shiftName,
                                          LocalDate workDate, Instant scheduledStartAt, Instant scheduledEndAt,
                                          int scheduledBreakMinutes, String status, String note, String cancelReason,
                                          Instant cancelledAt, Long attendanceId) {
    }

    public record ReasonRequest(@NotBlank @Size(max = 255) String reason) {
    }

    /** PRESENT cần giờ vào/ra; workedMinutes bỏ trống = (ra − vào) − phút nghỉ của ca. ABSENT không có giờ. */
    public record AttendanceRequest(@NotNull @Positive Long shiftAssignmentId, @NotNull @Pattern(regexp = "PRESENT|ABSENT") String status,
                                    Instant actualStartAt, Instant actualEndAt, @PositiveOrZero Integer workedMinutes,
                                    @Size(max = 255) String note) {
    }

    public record AdjustAttendanceRequest(@NotNull @Pattern(regexp = "PRESENT|ABSENT") String status, Instant actualStartAt,
                                          Instant actualEndAt, @PositiveOrZero Integer workedMinutes,
                                          @NotBlank @Size(max = 255) String reason) {
    }

    public record AttendanceResponse(long id, long shiftAssignmentId, long employeeId, String employeeName, LocalDate workDate,
                                     String status, Instant actualStartAt, Instant actualEndAt, Integer workedMinutes, String note,
                                     Long confirmedById, Instant confirmedAt) {
    }

    public record AttendanceAdjustmentView(long id, String oldStatus, String newStatus, Instant oldStartAt, Instant newStartAt,
                                           Instant oldEndAt, Instant newEndAt, Integer oldWorkedMinutes, Integer newWorkedMinutes,
                                           String reason, long adjustedById, Instant adjustedAt) {
    }
}
