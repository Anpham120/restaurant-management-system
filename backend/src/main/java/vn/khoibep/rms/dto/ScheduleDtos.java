package vn.khoibep.rms.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import vn.khoibep.rms.enums.Role;
import vn.khoibep.rms.model.ShiftAssignment;
import vn.khoibep.rms.model.WorkShift;

public final class ScheduleDtos {

    private ScheduleDtos() {
    }

    public record WorkShiftDto(Long id, String name, LocalTime startTime, LocalTime endTime, boolean active) {
        public static WorkShiftDto from(WorkShift s) {
            return new WorkShiftDto(s.getId(), s.getName(), s.getStartTime(), s.getEndTime(), s.isActive());
        }
    }

    /** Leaving {@code active} out keeps the current value. */
    public record WorkShiftRequest(@NotBlank @Size(max = 50) String name, @NotNull LocalTime startTime,
                                   @NotNull LocalTime endTime, Boolean active) {
    }

    /** Someone who can be scheduled: active and not resigned. */
    public record StaffDto(Long id, String fullName, Role role) {
    }

    public record AssignmentDto(Long id, Long employeeId, String employeeName, Long workShiftId, String shiftName,
                                LocalDate workDate, LocalTime startTime, LocalTime endTime) {
        public static AssignmentDto from(ShiftAssignment a) {
            WorkShift s = a.getWorkShift();
            return new AssignmentDto(a.getId(), a.getEmployee().getId(), a.getEmployee().getFullName(), s.getId(),
                    s.getName(), a.getWorkDate(), s.getStartTime(), s.getEndTime());
        }
    }

    public record AssignRequest(@NotNull Long employeeId, @NotNull Long workShiftId, @NotNull LocalDate workDate) {
    }

    /** Copies the seven days starting on {@code fromWeek} to the seven days starting on {@code toWeek}. */
    public record CopyWeekRequest(@NotNull LocalDate fromWeek, @NotNull LocalDate toWeek) {
    }

    public record CopyWeekResult(int copied, int skipped) {
    }
}
