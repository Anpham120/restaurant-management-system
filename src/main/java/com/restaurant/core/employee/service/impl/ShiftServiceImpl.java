package com.restaurant.core.employee.service.impl;

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
import com.restaurant.core.common.time.BusinessDateProvider;
import com.restaurant.core.employee.entity.Attendance;
import com.restaurant.core.employee.entity.AttendanceAdjustment;
import com.restaurant.core.employee.entity.Employee;
import com.restaurant.core.employee.entity.Shift;
import com.restaurant.core.employee.entity.ShiftAssignment;
import com.restaurant.core.employee.entity.WorkStatus;
import com.restaurant.core.employee.repository.AttendanceAdjustmentRepository;
import com.restaurant.core.employee.repository.AttendanceRepository;
import com.restaurant.core.employee.repository.EmployeeRepository;
import com.restaurant.core.employee.repository.ShiftAssignmentRepository;
import com.restaurant.core.employee.repository.ShiftQueryRepository;
import com.restaurant.core.employee.repository.ShiftRepository;
import com.restaurant.core.employee.service.ShiftService;

@Service
@Transactional(readOnly = true)
public class ShiftServiceImpl implements ShiftService {

    /** Khoảng truy vấn tối đa cho danh sách phân ca / chấm công. */
    private static final int MAX_RANGE_DAYS = 62;

    private final ShiftQueryRepository repo;
    private final ShiftRepository shiftRepository;
    private final ShiftAssignmentRepository assignmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final AttendanceAdjustmentRepository adjustmentRepository;
    private final EmployeeRepository employeeRepository;
    private final BusinessDateProvider clock;

    public ShiftServiceImpl(ShiftQueryRepository repo, ShiftRepository shiftRepository, ShiftAssignmentRepository assignmentRepository,
                            AttendanceRepository attendanceRepository, AttendanceAdjustmentRepository adjustmentRepository,
                            EmployeeRepository employeeRepository, BusinessDateProvider clock) {
        this.repo = repo;
        this.shiftRepository = shiftRepository;
        this.assignmentRepository = assignmentRepository;
        this.attendanceRepository = attendanceRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.employeeRepository = employeeRepository;
        this.clock = clock;
    }

    // ---------- ca
    @Override
    public List<ShiftResponse> getAllShifts(boolean activeOnly) {
        return repo.findShifts(activeOnly);
    }

    @Override
    @Transactional
    public ShiftResponse createShift(ShiftRequest r) {
        Shift shift = shiftRepository.saveAndFlush(new Shift(r.name().strip(), r.startTime(), r.endTime(), breakOf(r), isActive(r.active())));
        return repo.findShift(shift.getId()).orElseThrow();
    }

    @Override
    @Transactional
    public ShiftResponse updateShift(long id, ShiftRequest r) {
        Shift shift = shiftRepository.findById(id).orElseThrow(() -> notFound("Không tìm thấy ca " + id));
        shift.changeDetails(r.name().strip(), r.startTime(), r.endTime(), breakOf(r), isActive(r.active()));
        shiftRepository.flush();
        return repo.findShift(id).orElseThrow();
    }

    // ---------- phân ca
    @Override
    @Transactional
    public ShiftAssignmentResponse assignShift(AssignShiftRequest r) {
        Shift shift = shiftRepository.findById(r.shiftId()).filter(Shift::isActive).orElse(null);
        Employee employee = employeeRepository.findByIdForUpdate(r.employeeId())
                .filter(e -> e.getWorkStatus() == WorkStatus.ACTIVE).orElse(null);
        if (shift == null || employee == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_ASSIGNMENT",
                    "Ca không tồn tại/ngừng dùng hoặc nhân viên không còn làm việc");
        }
        ShiftAssignment assignment = ShiftAssignment.schedule(employee.getId(), shift, r.workDate(), blank(r.note()),
                BusinessDateProvider.RESTAURANT_ZONE);
        if (assignmentRepository.existsOverlapping(employee.getId(), assignment.getScheduledStartAt(), assignment.getScheduledEndAt())) {
            throw new ApiException(HttpStatus.CONFLICT, "OVERLAP", "Nhân viên đã có ca trùng khoảng thời gian này");
        }
        return getAssignmentById(assignmentRepository.saveAndFlush(assignment).getId());
    }

    @Override
    public ShiftAssignmentResponse getAssignmentById(long id) {
        return repo.findAssignment(id).orElseThrow(() -> notFound("Không tìm thấy phân ca " + id));
    }

    @Override
    public List<ShiftAssignmentResponse> getAssignments(LocalDate from, LocalDate to, Long employeeId) {
        checkRange(from, to);
        return repo.findAssignments(from, to, employeeId);
    }

    /** Chỉ hủy ca SCHEDULED chưa chấm công. */
    @Override
    @Transactional
    public ShiftAssignmentResponse cancelAssignment(long id, long actorId, String reason) {
        ShiftAssignment assignment = assignmentRepository.findByIdForUpdate(id).orElseThrow(() -> notFound("Không tìm thấy phân ca " + id));
        boolean attended = attendanceRepository.existsByShiftAssignmentId(id);
        if (!assignment.isScheduled() || attended) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_CANCELLABLE",
                    "Phân ca " + id + " đang " + assignment.getStatus() + (attended ? ", đã chấm công" : "") + " — không hủy được");
        }
        assignment.cancel(reason.strip(), actorId, clock.now());
        assignmentRepository.flush();
        return getAssignmentById(id);
    }

    // ---------- chấm công
    /** Chấm một lần cho mỗi ca (UNIQUE → 409 DUPLICATE); ca chuyển COMPLETED trong cùng transaction. */
    @Override
    @Transactional
    public AttendanceResponse recordAttendance(long actorId, AttendanceRequest r) {
        ShiftAssignment assignment = assignmentRepository.findByIdForUpdate(r.shiftAssignmentId())
                .orElseThrow(() -> notFound("Không tìm thấy phân ca " + r.shiftAssignmentId()));
        if (assignment.isCancelled()) {
            throw new ApiException(HttpStatus.CONFLICT, "ASSIGNMENT_CANCELLED", "Phân ca " + r.shiftAssignmentId() + " đã hủy");
        }
        if (attendanceRepository.existsByShiftAssignmentId(assignment.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE", "Phân ca " + assignment.getId() + " đã chấm công: dùng điều chỉnh chấm công");
        }
        Times t = times(r.status(), r.actualStartAt(), r.actualEndAt(), r.workedMinutes(), assignment.getScheduledBreakMinutes());
        Attendance attendance = new Attendance(assignment.getId(), blank(r.note()));
        attendance.record(r.status(), t.start, t.end, t.worked, actorId, clock.now());
        attendanceRepository.saveAndFlush(attendance);
        if (assignment.isScheduled()) {
            assignment.markCompleted();
            assignmentRepository.flush();
        }
        return getAttendanceById(attendance.getId());
    }

    /** Lưu giá trị cũ/mới vào attendance_adjustments (chỉ-thêm) rồi cập nhật bản chấm công — một transaction. */
    @Override
    @Transactional
    public AttendanceResponse adjustAttendance(long attendanceId, long actorId, AdjustAttendanceRequest r) {
        Attendance attendance = attendanceRepository.findByIdForUpdate(attendanceId)
                .orElseThrow(() -> notFound("Không tìm thấy bản chấm công " + attendanceId));
        int breakMinutes = assignmentRepository.findById(attendance.getShiftAssignmentId()).orElseThrow().getScheduledBreakMinutes();
        Times t = times(r.status(), r.actualStartAt(), r.actualEndAt(), r.workedMinutes(), breakMinutes);
        Instant now = clock.now();
        adjustmentRepository.save(AttendanceAdjustment.before(attendance, r.status(), t.start, t.end, t.worked, r.reason().strip(), actorId, now));
        attendance.record(r.status(), t.start, t.end, t.worked, actorId, now);
        attendanceRepository.flush();
        return getAttendanceById(attendanceId);
    }

    @Override
    public AttendanceResponse getAttendanceById(long id) {
        return repo.findAttendance(id).orElseThrow(() -> notFound("Không tìm thấy bản chấm công " + id));
    }

    @Override
    public List<AttendanceResponse> getAttendances(LocalDate from, LocalDate to, Long employeeId) {
        checkRange(from, to);
        return repo.findAttendances(from, to, employeeId);
    }

    @Override
    public List<AttendanceAdjustmentView> getAttendanceAdjustments(long attendanceId) {
        getAttendanceById(attendanceId);
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

    private static boolean isActive(Boolean active) {
        return active == null || active;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}
