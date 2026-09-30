package vn.bnn.rms.attendance;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AttendanceDtos {

    private AttendanceDtos() {
    }

    /** workDate is the scheduled day, or the Vietnam-time day of the clock-in for work outside the schedule. */
    public record AttendanceDto(Long id, Long employeeId, String employeeName, Long shiftAssignmentId,
                                String shiftName, LocalDate workDate, LocalTime shiftStart, LocalTime shiftEnd,
                                Instant checkInAt, Instant checkOutAt, int lateMinutes, int earlyMinutes,
                                Integer workedMinutes, String editReason, String editedByName, Instant editedAt) {
    }

    /** What the clock screen needs: the open clock-in, if any, and today's shifts. */
    public record ClockStatusDto(AttendanceDto current, List<TodayShift> todayShifts) {
    }

    public record TodayShift(Long assignmentId, String shiftName, LocalTime startTime, LocalTime endTime,
                             boolean done) {
    }

    /** The clock-out may stay empty only while the person is still in the shift. */
    public record EditAttendanceRequest(@NotNull Instant checkInAt, Instant checkOutAt,
                                        @NotBlank @Size(max = 300) String reason) {
    }

    public record AddAttendanceRequest(@NotNull Long employeeId, Long shiftAssignmentId, @NotNull Instant checkInAt,
                                       @NotNull Instant checkOutAt, @NotBlank @Size(max = 300) String reason) {
    }
}
