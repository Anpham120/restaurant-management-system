package com.restaurant.core.employee.service;

import java.time.LocalDate;
import java.util.List;

import com.restaurant.core.employee.dto.request.ShiftRequests.AdjustAttendanceRequest;
import com.restaurant.core.employee.dto.request.ShiftRequests.AssignShiftRequest;
import com.restaurant.core.employee.dto.response.ShiftResponses.AttendanceAdjustmentView;
import com.restaurant.core.employee.dto.request.ShiftRequests.AttendanceRequest;
import com.restaurant.core.employee.dto.response.ShiftResponses.AttendanceResponse;
import com.restaurant.core.employee.dto.response.ShiftResponses.ShiftAssignmentResponse;
import com.restaurant.core.employee.dto.request.ShiftRequests.ShiftRequest;
import com.restaurant.core.employee.dto.response.ShiftResponses.ShiftResponse;

/** Ca làm, phân ca, chấm công và điều chỉnh công. Owner: Nguyễn Quang Hiếu. */
public interface ShiftService {

    List<ShiftResponse> getAllShifts(boolean activeOnly);

    ShiftResponse createShift(ShiftRequest r);

    ShiftResponse updateShift(long id, ShiftRequest r);

    ShiftAssignmentResponse assignShift(AssignShiftRequest r);

    ShiftAssignmentResponse getAssignmentById(long id);

    List<ShiftAssignmentResponse> getAssignments(LocalDate from, LocalDate to, Long employeeId);

    /** Chỉ hủy ca SCHEDULED chưa chấm công. */
    ShiftAssignmentResponse cancelAssignment(long id, long actorId, String reason);

    /** Chấm một lần cho mỗi ca (UNIQUE → 409 DUPLICATE); ca chuyển COMPLETED trong cùng transaction. */
    AttendanceResponse recordAttendance(long actorId, AttendanceRequest r);

    /** Lưu giá trị cũ/mới vào attendance_adjustments (chỉ-thêm) rồi cập nhật bản chấm công — một transaction. */
    AttendanceResponse adjustAttendance(long attendanceId, long actorId, AdjustAttendanceRequest r);

    AttendanceResponse getAttendanceById(long id);

    List<AttendanceResponse> getAttendances(LocalDate from, LocalDate to, Long employeeId);

    List<AttendanceAdjustmentView> getAttendanceAdjustments(long attendanceId);
}
