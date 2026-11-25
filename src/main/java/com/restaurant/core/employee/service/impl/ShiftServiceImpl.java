package com.restaurant.core.employee.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.restaurant.core.common.exception.ApiException;
import com.restaurant.core.employee.dto.request.ShiftRequests.AdjustAttendanceRequest;
import com.restaurant.core.employee.dto.request.ShiftRequests.AssignShiftRequest;
import com.restaurant.core.employee.dto.request.ShiftRequests.BulkAssignShiftRequest;
import com.restaurant.core.employee.dto.request.ShiftRequests.MonthlyAssignShiftRequest;
import com.restaurant.core.employee.dto.request.ShiftRequests.MonthlyShiftDay;
import com.restaurant.core.employee.dto.response.ShiftResponses.AttendanceAdjustmentView;
import com.restaurant.core.employee.dto.request.ShiftRequests.AttendanceRequest;
import com.restaurant.core.employee.dto.response.ShiftResponses.AttendanceResponse;
import com.restaurant.core.employee.dto.response.ShiftResponses.MonthlyAssignmentResponse;
import com.restaurant.core.employee.dto.response.ShiftResponses.ShiftAssignmentResponse;
import com.restaurant.core.employee.dto.request.ShiftRequests.ShiftRequest;
import com.restaurant.core.employee.dto.response.ShiftResponses.ShiftResponse;
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

import static com.restaurant.core.common.util.TextUtils.blankToNull;

@Service
@Transactional(readOnly = true)
public class ShiftServiceImpl implements ShiftService {

    /** Khoảng truy vấn tối đa cho danh sách phân ca / chấm công. */
    private static final int MAX_RANGE_DAYS = 62;
    private static final String INVALID_SINGLE_ASSIGNMENT = "Ca không tồn tại/ngừng dùng hoặc nhân viên không còn làm việc";

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
        Shift shift = shiftRepository.findById(id).orElseThrow(() -> notFoundError("Không tìm thấy ca " + id));
        shift.changeDetails(r.name().strip(), r.startTime(), r.endTime(), breakOf(r), isActive(r.active()));
        shiftRepository.flush();
        return repo.findShift(id).orElseThrow();
    }

    // ---------- phân ca
    @Override
    @Transactional
    public ShiftAssignmentResponse assignShift(AssignShiftRequest r) {
        return saveAssignments(List.of(r.employeeId()), r.shiftId(), r.workDate(), r.note(), false).getFirst();
    }

    @Override
    @Transactional
    public List<ShiftAssignmentResponse> assignShifts(BulkAssignShiftRequest r) {
        List<Long> employeeIds = sortedUniqueEmployeeIds(r.employeeIds());
        return saveAssignments(employeeIds, r.shiftId(), r.workDate(), r.note(), true);
    }

    @Override
    @Transactional
    public MonthlyAssignmentResponse assignMonthlyShifts(MonthlyAssignShiftRequest r) {
        List<Long> employeeIds = sortedUniqueEmployeeIds(r.employeeIds());
        NavigableMap<LocalDate, Long> shiftIdsByDate = monthlyShiftIds(r);
        Map<Long, Shift> activeShifts = shiftRepository.findAllById(shiftIdsByDate.values()).stream()
                .filter(Shift::isActive).collect(Collectors.toMap(Shift::getId, shift -> shift));
        shiftIdsByDate.forEach((date, id) -> {
            if (!activeShifts.containsKey(id)) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_ASSIGNMENT",
                        "Ca " + id + " ngày " + date + " không tồn tại hoặc đã ngừng dùng");
            }
        });

        // Cùng thứ tự khóa với phân ca một ngày để các lượt không chờ khóa vòng tròn.
        Map<Long, Employee> employees = employeeRepository.findAllByIdForUpdate(employeeIds).stream()
                .collect(Collectors.toMap(Employee::getId, employee -> employee));
        for (long id : employeeIds) {
            Employee employee = employees.get(id);
            if (employee == null || employee.getWorkStatus() != WorkStatus.ACTIVE) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_ASSIGNMENT",
                        employeeLabel(employee, id) + " không tồn tại hoặc không còn làm việc");
            }
        }

        var zone = BusinessDateProvider.RESTAURANT_ZONE;
        Instant start = shiftIdsByDate.firstKey().atStartOfDay(zone).toInstant();
        // Ca cuối kế hoạch có thể kết thúc vào ngày hôm sau, kể cả sang tháng mới.
        Instant end = shiftIdsByDate.lastKey().plusDays(2).atStartOfDay(zone).toInstant();
        Map<Long, List<ShiftAssignment>> existingByEmployee = assignmentRepository.findOverlapping(employeeIds, start, end).stream()
                .collect(Collectors.groupingBy(ShiftAssignment::getEmployeeId));

        String note = blankToNull(r.note());
        List<ShiftAssignment> pending = new ArrayList<>();
        int unchanged = 0;
        for (long id : employeeIds) {
            Employee employee = employees.get(id);
            List<ShiftAssignment> existing = existingByEmployee.getOrDefault(id, List.of());
            ShiftAssignment previous = null;
            for (var day : shiftIdsByDate.entrySet()) {
                ShiftAssignment assignment = ShiftAssignment.schedule(employee, activeShifts.get(day.getValue()), day.getKey(), note, zone);
                if (previous != null && previous.overlaps(assignment)) {
                    throw monthlyOverlap(employee, day.getKey());
                }
                previous = assignment;
                var overlapping = existing.stream().filter(assignment::overlaps).findFirst();
                if (overlapping.isEmpty()) {
                    pending.add(assignment);
                } else if (assignment.hasSameSchedule(overlapping.get())) {
                    unchanged++;
                } else {
                    throw monthlyOverlap(employee, day.getKey());
                }
            }
        }
        assignmentRepository.saveAllAndFlush(pending);
        return new MonthlyAssignmentResponse(pending.size(), unchanged);
    }

    private static NavigableMap<LocalDate, Long> monthlyShiftIds(MonthlyAssignShiftRequest r) {
        YearMonth month = YearMonth.parse(r.month());
        NavigableMap<LocalDate, Long> shiftIdsByDate = new TreeMap<>();
        for (MonthlyShiftDay day : r.days()) {
            if (!month.equals(YearMonth.from(day.workDate()))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Ngày " + day.workDate() + " không thuộc tháng " + month);
            }
            if (shiftIdsByDate.putIfAbsent(day.workDate(), day.shiftId()) != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Ngày " + day.workDate() + " bị trùng");
            }
        }
        return shiftIdsByDate;
    }

    private static List<Long> sortedUniqueEmployeeIds(List<Long> ids) {
        List<Long> ordered = ids.stream().distinct().sorted().toList();
        if (ordered.size() != ids.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Danh sách nhân viên bị trùng");
        }
        return ordered;
    }

    private static ApiException monthlyOverlap(Employee employee, LocalDate date) {
        return new ApiException(HttpStatus.CONFLICT, "OVERLAP",
                employeeLabel(employee, employee.getId()) + " có lịch trùng giờ vào ngày " + date);
    }

    private List<ShiftAssignmentResponse> saveAssignments(List<Long> employeeIds, long shiftId, LocalDate workDate, String note,
                                                          boolean bulk) {
        Shift shift = shiftRepository.findById(shiftId).filter(Shift::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_ASSIGNMENT",
                        bulk ? "Ca không tồn tại hoặc đã ngừng dùng" : INVALID_SINGLE_ASSIGNMENT));
        List<ShiftAssignment> assignments = new ArrayList<>(employeeIds.size());
        String cleanedNote = blankToNull(note);
        for (long employeeId : employeeIds) {
            Employee employee = employeeRepository.findByIdForUpdate(employeeId).orElse(null);
            if (employee == null || employee.getWorkStatus() != WorkStatus.ACTIVE) {
                String message = bulk ? employeeLabel(employee, employeeId) + " không tồn tại hoặc không còn làm việc"
                        : INVALID_SINGLE_ASSIGNMENT;
                throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_ASSIGNMENT", message);
            }
            ShiftAssignment assignment = ShiftAssignment.schedule(employee, shift, workDate, cleanedNote,
                    BusinessDateProvider.RESTAURANT_ZONE);
            if (assignmentRepository.existsOverlapping(employeeId, assignment.getScheduledStartAt(), assignment.getScheduledEndAt())) {
                String subject = bulk ? employeeLabel(employee, employeeId) : "Nhân viên";
                throw new ApiException(HttpStatus.CONFLICT, "OVERLAP", subject + " đã có ca trùng khoảng thời gian này");
            }
            assignments.add(assignment);
        }
        return assignmentRepository.saveAllAndFlush(assignments).stream()
                .map(assignment -> getShiftAssignmentById(assignment.getId())).toList();
    }

    private static String employeeLabel(Employee employee, long id) {
        return employee == null ? "Nhân viên #" + id : employee.getFullName() + " (" + employee.getEmployeeCode() + ")";
    }

    @Override
    public ShiftAssignmentResponse getShiftAssignmentById(long id) {
        return repo.findShiftAssignment(id).orElseThrow(() -> notFoundError("Không tìm thấy phân ca " + id));
    }

    @Override
    public List<ShiftAssignmentResponse> getShiftAssignments(LocalDate from, LocalDate to, Long employeeId) {
        validateDateRange(from, to);
        return repo.findShiftAssignments(from, to, employeeId);
    }

    /** Chỉ hủy ca SCHEDULED chưa chấm công. */
    @Override
    @Transactional
    public ShiftAssignmentResponse cancelShiftAssignment(long id, long actorId, String reason) {
        ShiftAssignment assignment = assignmentRepository.findByIdForUpdate(id).orElseThrow(() -> notFoundError("Không tìm thấy phân ca " + id));
        boolean attended = attendanceRepository.existsByShiftAssignment_Id(id);
        if (!assignment.isScheduled() || attended) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_CANCELLABLE",
                    "Phân ca " + id + " đang " + assignment.getStatus() + (attended ? ", đã chấm công" : "") + " — không hủy được");
        }
        assignment.cancel(reason.strip(), employeeRepository.getReferenceById(actorId), clock.now());
        assignmentRepository.flush();
        return getShiftAssignmentById(id);
    }

    // ---------- chấm công
    /** Chấm một lần cho mỗi ca (UNIQUE → 409 DUPLICATE); ca chuyển COMPLETED trong cùng transaction. */
    @Override
    @Transactional
    public AttendanceResponse recordAttendance(long actorId, AttendanceRequest r) {
        ShiftAssignment assignment = assignmentRepository.findByIdForUpdate(r.shiftAssignmentId())
                .orElseThrow(() -> notFoundError("Không tìm thấy phân ca " + r.shiftAssignmentId()));
        if (assignment.isCancelled()) {
            throw new ApiException(HttpStatus.CONFLICT, "ASSIGNMENT_CANCELLED", "Phân ca " + r.shiftAssignmentId() + " đã hủy");
        }
        if (attendanceRepository.existsByShiftAssignment_Id(assignment.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE", "Phân ca " + assignment.getId() + " đã chấm công: dùng điều chỉnh chấm công");
        }
        Times t = calculateWorkTimes(r.status(), r.actualStartAt(), r.actualEndAt(), r.workedMinutes(), assignment.getScheduledBreakMinutes());
        Attendance attendance = new Attendance(assignment, blankToNull(r.note()));
        attendance.record(r.status(), t.start, t.end, t.worked, employeeRepository.getReferenceById(actorId), clock.now());
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
                .orElseThrow(() -> notFoundError("Không tìm thấy bản chấm công " + attendanceId));
        int breakMinutes = assignmentRepository.findById(attendance.getShiftAssignmentId()).orElseThrow().getScheduledBreakMinutes();
        Times t = calculateWorkTimes(r.status(), r.actualStartAt(), r.actualEndAt(), r.workedMinutes(), breakMinutes);
        Instant now = clock.now();
        adjustmentRepository.save(AttendanceAdjustment.before(attendance, r.status(), t.start, t.end, t.worked, r.reason().strip(), employeeRepository.getReferenceById(actorId), now));
        attendance.record(r.status(), t.start, t.end, t.worked, employeeRepository.getReferenceById(actorId), now);
        attendanceRepository.flush();
        return getAttendanceById(attendanceId);
    }

    @Override
    public AttendanceResponse getAttendanceById(long id) {
        return repo.findAttendance(id).orElseThrow(() -> notFoundError("Không tìm thấy bản chấm công " + id));
    }

    @Override
    public List<AttendanceResponse> getAttendances(LocalDate from, LocalDate to, Long employeeId) {
        validateDateRange(from, to);
        return repo.findAttendances(from, to, employeeId);
    }

    @Override
    public List<AttendanceAdjustmentView> getAttendanceAdjustments(long attendanceId) {
        getAttendanceById(attendanceId);
        return repo.findAttendanceAdjustments(attendanceId);
    }

    private record Times(Instant start, Instant end, Integer worked) {
    }

    /** ABSENT: không giờ, không công. PRESENT: cần vào < ra; công mặc định = khoảng có mặt − phút nghỉ (≥ 0). */
    private static Times calculateWorkTimes(String status, Instant start, Instant end, Integer worked, int breakMinutes) {
        if ("ABSENT".equals(status)) {
            return new Times(null, null, null);
        }
        if (start == null || end == null || !end.isAfter(start)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_DATA", "Có mặt cần giờ vào và giờ ra, giờ ra sau giờ vào");
        }
        int computed = (int) Math.max(0, Duration.between(start, end).toMinutes() - breakMinutes);
        return new Times(start, end, worked == null ? computed : worked);
    }

    private static void validateDateRange(LocalDate from, LocalDate to) {
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


    static ApiException notFoundError(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}
