package com.restaurant.core.employee.dto.request;

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
public final class ShiftRequests {

    private ShiftRequests() {
    }

    public record ShiftRequest(@NotBlank @Size(max = 50) String name, @NotNull LocalTime startTime, @NotNull LocalTime endTime,
                               @PositiveOrZero @Max(600) Integer breakMinutes, Boolean active) {
    }

    public record AssignShiftRequest(@NotNull @Positive Long employeeId, @NotNull @Positive Long shiftId, @NotNull LocalDate workDate,
                                     @Size(max = 255) String note) {
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
}
