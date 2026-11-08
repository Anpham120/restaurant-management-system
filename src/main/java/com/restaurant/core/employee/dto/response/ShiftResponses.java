package com.restaurant.core.employee.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/** Ca làm, phân ca, chấm công (1.3–1.4). Giờ ca là giờ Việt Nam; endTime ≤ startTime nghĩa là ca qua đêm. */
public final class ShiftResponses {

    private ShiftResponses() {
    }

    public record ShiftResponse(long id, String name, LocalTime startTime, LocalTime endTime, int breakMinutes, boolean active) {
    }

    public record ShiftAssignmentResponse(long id, long employeeId, String employeeName, long shiftId, String shiftName,
                                          LocalDate workDate, Instant scheduledStartAt, Instant scheduledEndAt,
                                          int scheduledBreakMinutes, String status, String note, String cancelReason,
                                          Instant cancelledAt, Long attendanceId) {
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
