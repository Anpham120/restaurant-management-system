package com.restaurant.core.employee.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.restaurant.core.common.exception.ApiException;
import com.restaurant.core.employee.dto.ShiftDtos.AdjustAttendanceRequest;
import com.restaurant.core.employee.dto.ShiftDtos.AssignShiftRequest;
import com.restaurant.core.employee.dto.ShiftDtos.AttendanceAdjustmentView;
import com.restaurant.core.employee.dto.ShiftDtos.AttendanceRequest;
import com.restaurant.core.employee.dto.ShiftDtos.AttendanceResponse;
import com.restaurant.core.employee.dto.ShiftDtos.ShiftAssignmentResponse;
import com.restaurant.core.employee.dto.ShiftDtos.ShiftRequest;
import com.restaurant.core.employee.dto.ShiftDtos.ShiftResponse;
import com.restaurant.core.employee.repository.ShiftRepository;
import com.restaurant.core.employee.repository.ShiftRepository.AssignmentLock;
import com.restaurant.core.employee.repository.ShiftRepository.AttendanceLock;

/** Ca làm, phân ca, chấm công và điều chỉnh công. Owner: Nguyễn Quang Hiếu. */
@Service
public class ShiftService {

    /** Khoảng truy vấn tối đa cho danh sách phân ca / chấm công. */
    private static final int MAX_RANGE_DAYS = 62;

    private final ShiftRepository repo;

    public ShiftService(ShiftRepository repo) {
        this.repo = repo;
    }

    // ---------- ca
    public List<ShiftResponse> shifts(boolean activeOnly) {
        return repo.findShifts(activeOnly);
    }

    public ShiftResponse createShift(ShiftRequest r) {
        long id = repo.insertShift(r.name().strip(), r.startTime(), r.endTime(), breakOf(r), r.active() == null || r.active());
        return repo.findShift(id).orElseThrow();
    }

    public ShiftResponse updateShift(long id, ShiftRequest r) {
        if (repo.updateShift(id, r.name().strip(), r.startTime(), r.endTime(), breakOf(r), r.active() == null || r.active()) == 0) {
            throw notFound("Không tìm thấy ca " + id);
        }
        return repo.findShift(id).orElseThrow();
    }

    // ---------- phân ca
    public ShiftAssignmentResponse assign(AssignShiftRequest r) {
        long id = repo.insertAssignment(r.employeeId(), r.shiftId(), r.workDate(), blank(r.note()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_ASSIGNMENT",
                        "Ca không tồn tại/ngừng dùng hoặc nhân viên không còn làm việc"));
        return assignment(id);
    }

    public ShiftAssignmentResponse assignment(long id) {
        return repo.findAssignment(id).orElseThrow(() -> notFound("Không tìm thấy phân ca " + id));
    }

    public List<ShiftAssignmentResponse> assignments(LocalDate from, LocalDate to, Long employeeId) {
        checkRange(from, to);
        return repo.findAssignments(from, to, employeeId);
    }

    /** Chỉ hủy ca SCHEDULED chưa chấm công. */
    public ShiftAssignmentResponse cancel(long id, long actorId, String reason) {
        if (repo.cancelAssignment(id, actorId, reason.strip()) == 0) {
            ShiftAssignmentResponse a = assignment(id);
            throw new ApiException(HttpStatus.CONFLICT, "NOT_CANCELLABLE",
                    "Phân ca " + id + " đang " + a.status() + (a.attendanceId() == null ? "" : ", đã chấm công") + " — không hủy được");
        }
        return assignment(id);
    }

    // ---------- chấm công
    /** Chấm một lần cho mỗi ca (UNIQUE → 409 DUPLICATE); ca chuyển COMPLETED trong cùng transaction. */
    @Transactional
    public AttendanceResponse record(long actorId, AttendanceRequest r) {
        AssignmentLock a = repo.lockAssignment(r.shiftAssignmentId())
                .orElseThrow(() -> notFound("Không tìm thấy phân ca " + r.shiftAssignmentId()));
        if ("CANCELLED".equals(a.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "ASSIGNMENT_CANCELLED", "Phân ca " + r.shiftAssignmentId() + " đã hủy");
        }
        Times t = times(r.status(), r.actualStartAt(), r.actualEndAt(), r.workedMinutes(), a.scheduledBreakMinutes());
        long id = repo.insertAttendance(r.shiftAssignmentId(), r.status(), t.start, t.end, t.worked, blank(r.note()), actorId);
        repo.completeAssignment(r.shiftAssignmentId());
        return attendance(id);
    }

    /** Lưu giá trị cũ/mới vào attendance_adjustments (chỉ-thêm) rồi cập nhật bản chấm công — một transaction. */
    @Transactional
    public AttendanceResponse adjust(long attendanceId, long actorId, AdjustAttendanceRequest r) {
        AttendanceLock old = repo.lockAttendance(attendanceId).orElseThrow(() -> notFound("Không tìm thấy bản chấm công " + attendanceId));
        Times t = times(r.status(), r.actualStartAt(), r.actualEndAt(), r.workedMinutes(), old.scheduledBreakMinutes());
        repo.insertAdjustment(old, r.status(), t.start, t.end, t.worked, r.reason().strip(), actorId);
        repo.updateAttendance(attendanceId, r.status(), t.start, t.end, t.worked, actorId);
        return attendance(attendanceId);
    }

    public AttendanceResponse attendance(long id) {
        return repo.findAttendance(id).orElseThrow(() -> notFound("Không tìm thấy bản chấm công " + id));
    }

    public List<AttendanceResponse> attendances(LocalDate from, LocalDate to, Long employeeId) {
        checkRange(from, to);
        return repo.findAttendances(from, to, employeeId);
    }

    public List<AttendanceAdjustmentView> adjustments(long attendanceId) {
        attendance(attendanceId);
        return repo.findAdjustments(attendanceId);
    }

    private record Times(Instant start, Instant end, Integer worked) {
    }

    /** ABSENT: không giờ, không công. PRESENT: cần vào < ra; công mặc định = khoảng có mặt − phút nghỉ (≥ 0). */
    private static Times times(String status, Instant start, Instant end, Integer worked, int breakMinutes) {
        if ("ABSENT".equals(status)) {
            return new Times(null, null, null);
        }
        if (start == null || end == null || !end.isAfter(start)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_DATA", "Có mặt cần giờ vào và giờ ra, giờ ra sau giờ vào");
        }
        int computed = (int) Math.max(0, Duration.between(start, end).toMinutes() - breakMinutes);
        return new Times(start, end, worked == null ? computed : worked);
    }

    private static void checkRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to) || from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Khoảng ngày không hợp lệ (from ≤ to, tối đa " + MAX_RANGE_DAYS + " ngày)");
        }
    }

    private static int breakOf(ShiftRequest r) {
        return r.breakMinutes() == null ? 0 : r.breakMinutes();
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}
