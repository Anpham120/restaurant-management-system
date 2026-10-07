package vn.khoibep.rms.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.khoibep.rms.common.exception.ApiException;
import vn.khoibep.rms.common.util.DateRange;
import vn.khoibep.rms.dto.AttendanceDtos.AddAttendanceRequest;
import vn.khoibep.rms.dto.AttendanceDtos.AttendanceDto;
import vn.khoibep.rms.dto.AttendanceDtos.ClockStatusDto;
import vn.khoibep.rms.dto.AttendanceDtos.EditAttendanceRequest;
import vn.khoibep.rms.dto.AttendanceDtos.TodayShift;
import vn.khoibep.rms.model.Attendance;
import vn.khoibep.rms.model.Employee;
import vn.khoibep.rms.model.ShiftAssignment;
import vn.khoibep.rms.model.WorkShift;
import vn.khoibep.rms.repository.AttendanceRepository;
import vn.khoibep.rms.repository.EmployeeRepository;
import vn.khoibep.rms.repository.ShiftAssignmentRepository;

/** FR-14, BR-25: clocking in and out, and managers' corrections. */
@Service
@RequiredArgsConstructor
public class AttendanceService {

    /** How early one may clock in before the shift starts (BR-25). */
    static final Duration EARLY_CLOCK_IN = Duration.ofMinutes(15);
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final AttendanceRepository attendance;
    private final ShiftAssignmentRepository assignments;
    private final EmployeeRepository employees;
    private final PayrollLock payrollLock;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ClockStatusDto status(Long employeeId) {
        AttendanceDto current = attendance.findByEmployeeIdAndCheckOutAtIsNull(employeeId).map(this::toDto).orElse(null);
        List<TodayShift> today = assignments.findForEmployeeOn(employeeId, LocalDate.now(clock)).stream()
                .map(a -> new TodayShift(a.getId(), a.getWorkShift().getName(), a.getWorkShift().getStartTime(),
                        a.getWorkShift().getEndTime(), attendance.existsByShiftAssignmentId(a.getId())))
                .toList();
        return new ClockStatusDto(current, today);
    }

    /** FR-14.1, BR-25: only for a shift scheduled today, from 15 minutes before it starts until it ends. */
    @Transactional
    public AttendanceDto checkIn(Long employeeId) {
        if (attendance.findByEmployeeIdAndCheckOutAtIsNull(employeeId).isPresent()) {
            throw ApiException.conflict("Bạn đang trong ca");
        }
        Instant now = clock.instant();
        ZoneId zone = clock.getZone();
        List<ShiftAssignment> open = assignments.findForEmployeeOn(employeeId, LocalDate.now(clock)).stream()
                .filter(a -> !attendance.existsByShiftAssignmentId(a.getId()))
                .toList();
        if (open.isEmpty()) {
            throw ApiException.conflict("Hôm nay bạn không có ca nào để vào");
        }
        ShiftAssignment shift = open.stream()
                .filter(a -> !now.isBefore(a.startsAt(zone).minus(EARLY_CLOCK_IN)) && now.isBefore(a.endsAt(zone)))
                .findFirst()
                .orElseThrow(() -> tooEarlyOrOver(open, now, zone));
        Attendance record = new Attendance(employees.getReferenceById(employeeId), shift, now);
        record.recompute(zone);
        return toDto(attendance.saveAndFlush(record));
    }

    /** FR-14.1: worked time is the real time between clock-in and clock-out (BR-25). */
    @Transactional
    public AttendanceDto checkOut(Long employeeId) {
        Attendance record = attendance.findByEmployeeIdAndCheckOutAtIsNull(employeeId)
                .orElseThrow(() -> ApiException.conflict("Bạn chưa vào ca"));
        Instant now = clock.instant();
        if (!now.isAfter(record.getCheckInAt())) {
            throw ApiException.conflict("Giờ ra phải sau giờ vào");
        }
        record.setCheckOutAt(now);
        record.recompute(clock.getZone());
        return toDto(record);
    }

    /** FR-14.3: by Vietnam-time day of the clock-in, for everyone or one person. */
    @Transactional(readOnly = true)
    public List<AttendanceDto> list(LocalDate from, LocalDate to, Long employeeId) {
        DateRange.check(from, to, ScheduleService.MAX_DAYS);
        Instant start = startOf(from);
        Instant end = startOf(to.plusDays(1));
        return toDtos(employeeId == null
                ? attendance.findInRange(start, end)
                : attendance.findInRangeFor(employeeId, start, end));
    }

    /** FR-14.4. */
    @Transactional(readOnly = true)
    public List<AttendanceDto> mine(Long employeeId, LocalDate from, LocalDate to) {
        DateRange.check(from, to, ScheduleService.MAX_DAYS);
        return toDtos(attendance.findInRangeFor(employeeId, startOf(from), startOf(to.plusDays(1))));
    }

    /**
     * FR-14.3: a correction needs a reason and is signed with who made it and when. A month whose payroll is
     * finalized is locked (BR-27).
     */
    @Transactional
    public AttendanceDto edit(Long id, EditAttendanceRequest request, Long managerId) {
        Attendance record = attendance.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy bản ghi chấm công"));
        if (request.checkOutAt() == null && record.getCheckOutAt() != null) {
            throw ApiException.badRequest("Cần nhập giờ ra");
        }
        checkTimes(request.checkInAt(), request.checkOutAt());
        payrollLock.requireOpen(dayOf(record.getCheckInAt()));
        payrollLock.requireOpen(dayOf(request.checkInAt()));
        record.setCheckInAt(request.checkInAt());
        record.setCheckOutAt(request.checkOutAt());
        sign(record, request.reason(), managerId);
        return toDto(record);
    }

    /** FR-14.3: a clock-in someone forgot, or work outside the schedule. */
    @Transactional
    public AttendanceDto add(AddAttendanceRequest request, Long managerId) {
        Employee employee = employees.findById(request.employeeId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy nhân viên"));
        checkTimes(request.checkInAt(), request.checkOutAt());
        payrollLock.requireOpen(dayOf(request.checkInAt()));
        ShiftAssignment shift = null;
        if (request.shiftAssignmentId() != null) {
            shift = assignments.findById(request.shiftAssignmentId())
                    .orElseThrow(() -> ApiException.notFound("Không tìm thấy ca đã xếp"));
            if (!shift.getEmployee().getId().equals(employee.getId())) {
                throw ApiException.badRequest("Ca này không phải của nhân viên đó");
            }
        }
        Attendance record = new Attendance(employee, shift, request.checkInAt());
        record.setCheckOutAt(request.checkOutAt());
        sign(record, request.reason(), managerId);
        return toDto(attendance.saveAndFlush(record));
    }

    private void sign(Attendance record, String reason, Long managerId) {
        record.recompute(clock.getZone());
        record.setEditReason(reason.trim());
        record.setEditedBy(managerId);
        record.setEditedAt(clock.instant());
    }

    private static void checkTimes(Instant checkIn, Instant checkOut) {
        if (checkOut != null && !checkOut.isAfter(checkIn)) {
            throw ApiException.badRequest("Giờ ra phải sau giờ vào");
        }
    }

    private static ApiException tooEarlyOrOver(List<ShiftAssignment> open, Instant now, ZoneId zone) {
        return open.stream()
                .filter(a -> now.isBefore(a.startsAt(zone)))
                .findFirst()
                .map(a -> ApiException.conflict("Chưa tới giờ vào ca. Sớm nhất lúc "
                        + HH_MM.format(a.getWorkShift().getStartTime().minus(EARLY_CLOCK_IN))))
                .orElseGet(() -> ApiException.conflict("Ca hôm nay đã kết thúc"));
    }

    private Instant startOf(LocalDate day) {
        return day.atStartOfDay(clock.getZone()).toInstant();
    }

    private LocalDate dayOf(Instant instant) {
        return LocalDate.ofInstant(instant, clock.getZone());
    }

    private AttendanceDto toDto(Attendance record) {
        return toDtos(List.of(record)).get(0);
    }

    private List<AttendanceDto> toDtos(List<Attendance> records) {
        Map<Long, String> editors = employees.findAllById(records.stream().map(Attendance::getEditedBy)
                        .filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Employee::getId, Employee::getFullName));
        ZoneId zone = clock.getZone();
        return records.stream().map(t -> {
            ShiftAssignment a = t.getShiftAssignment();
            WorkShift s = a == null ? null : a.getWorkShift();
            return new AttendanceDto(t.getId(), t.getEmployee().getId(), t.getEmployee().getFullName(),
                    a == null ? null : a.getId(), s == null ? null : s.getName(),
                    a == null ? LocalDate.ofInstant(t.getCheckInAt(), zone) : a.getWorkDate(),
                    s == null ? null : s.getStartTime(), s == null ? null : s.getEndTime(),
                    t.getCheckInAt(), t.getCheckOutAt(), t.getLateMinutes(), t.getEarlyMinutes(),
                    t.getWorkedMinutes(), t.getEditReason(), editors.get(t.getEditedBy()), t.getEditedAt());
        }).toList();
    }
}
