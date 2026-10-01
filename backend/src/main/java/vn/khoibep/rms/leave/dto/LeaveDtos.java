package vn.khoibep.rms.leave.dto;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import vn.khoibep.rms.leave.enums.LeaveStatus;
import vn.khoibep.rms.leave.enums.LeaveType;

public final class LeaveDtos {

    private LeaveDtos() {
    }

    /** days counts both ends. */
    public record LeaveDto(Long id, Long employeeId, String employeeName, LocalDate fromDate, LocalDate toDate, int days,
                           LeaveType type, String reason, LeaveStatus status, String decidedByName,
                           String decisionNote, Instant createdAt, Instant decidedAt) {
    }

    public record CreateLeaveRequest(@NotNull LocalDate fromDate, @NotNull LocalDate toDate, @NotNull LeaveType type,
                                     @NotBlank @Size(max = 300) String reason) {
    }

    /** Optional when approving, required when rejecting (BR-24). */
    public record DecisionRequest(@Size(max = 300) String note) {
    }
}
