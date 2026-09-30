package vn.bnn.rms.schedule.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.bnn.rms.common.exception.ApiException;
import vn.bnn.rms.common.util.DateRange;
import vn.bnn.rms.employee.entity.Employee;
import vn.bnn.rms.employee.repository.EmployeeRepository;
import vn.bnn.rms.leave.enums.LeaveStatus;
import vn.bnn.rms.leave.repository.LeaveRequestRepository;
import vn.bnn.rms.schedule.dto.ScheduleDtos.AssignRequest;
import vn.bnn.rms.schedule.dto.ScheduleDtos.AssignmentDto;
import vn.bnn.rms.schedule.dto.ScheduleDtos.CopyWeekResult;
import vn.bnn.rms.schedule.dto.ScheduleDtos.StaffDto;
import vn.bnn.rms.schedule.dto.ScheduleDtos.WorkShiftDto;
import vn.bnn.rms.schedule.dto.ScheduleDtos.WorkShiftRequest;
import vn.bnn.rms.schedule.entity.ShiftAssignment;
import vn.bnn.rms.schedule.entity.WorkShift;
import vn.bnn.rms.schedule.repository.ShiftAssignmentRepository;
import vn.bnn.rms.schedule.repository.WorkShiftRepository;

/** FR-13.1 → 13.4, BR-23: shift templates and who works when. */
@Service
@RequiredArgsConstructor
public class ScheduleService {

    /** Longest range one request may read. */
    public static final int MAX_DAYS = 62;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final WorkShiftRepository shifts;
    private final ShiftAssignmentRepository assignments;
    private final EmployeeRepository employees;
    private final LeaveRequestRepository leaves;

    @Transactional(readOnly = true)
    public List<WorkShiftDto> shifts() {
        return shifts.findAllByOrderByStartTimeAscNameAsc().stream().map(WorkShiftDto::from).toList();
    }

    @Transactional
    public WorkShiftDto createShift(WorkShiftRequest request) {
        if (shifts.existsByNameIgnoreCase(request.name().trim())) {
            throw ApiException.conflict("Ca mẫu đã tồn tại");
        }
        WorkShift shift = new WorkShift();
        apply(shift, request);
        return WorkShiftDto.from(shifts.save(shift));
    }

    @Transactional
    public WorkShiftDto updateShift(Long id, WorkShiftRequest request) {
        if (shifts.existsByNameIgnoreCaseAndIdNot(request.name().trim(), id)) {
            throw ApiException.conflict("Ca mẫu đã tồn tại");
        }
        WorkShift shift = shifts.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy ca mẫu"));
        apply(shift, request);
        return WorkShiftDto.from(shift);
    }

    /** People who can be scheduled (BR-23). */
    @Transactional(readOnly = true)
    public List<StaffDto> staff() {
        return employees.findAll(Sort.by("fullName")).stream()
                .filter(ScheduleService::working)
                .map(e -> new StaffDto(e.getId(), e.getFullName(), e.getRole()))
                .toList();
    }

    /** FR-13.2: the seven days starting on {@code from}. */
    @Transactional(readOnly = true)
    public List<AssignmentDto> week(LocalDate from) {
        return assignments.findInRange(from, from.plusDays(6)).stream().map(AssignmentDto::from).toList();
    }

    /** FR-13.4: an employee sees only their own schedule. */
    @Transactional(readOnly = true)
    public List<AssignmentDto> mine(Long employeeId, LocalDate from, LocalDate to) {
        DateRange.check(from, to, MAX_DAYS);
        return assignments.findForEmployee(employeeId, from, to).stream().map(AssignmentDto::from).toList();
    }

    @Transactional
    public AssignmentDto assign(AssignRequest request, Long managerId) {
        Employee employee = employees.findById(request.employeeId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy nhân viên"));
        if (!working(employee)) {
            throw ApiException.conflict("Nhân viên đã nghỉ việc hoặc bị khoá");
        }
        WorkShift shift = shifts.findById(request.workShiftId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy ca mẫu"));
        if (!shift.isActive()) {
            throw ApiException.conflict("Ca mẫu đã ngừng dùng");
        }
        String problem = conflictOf(employee, shift, request.workDate());
        if (problem != null) {
            throw ApiException.conflict(problem);
        }
        return AssignmentDto.from(assignments.saveAndFlush(
                new ShiftAssignment(employee, shift, request.workDate(), managerId)));
    }

    @Transactional
    public void unassign(Long id) {
        ShiftAssignment assignment = assignments.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy ca đã xếp"));
        assignments.delete(assignment);
        // Flush now so a blocking foreign key is reported as a normal error, not at commit time.
        assignments.flush();
    }

    /** FR-13.3: copies a week, skipping people who left and shifts that would clash (BR-23). */
    @Transactional
    public CopyWeekResult copyWeek(LocalDate fromWeek, LocalDate toWeek, Long managerId) {
        if (fromWeek.equals(toWeek)) {
            throw ApiException.badRequest("Tuần nguồn và tuần đích phải khác nhau");
        }
        long offset = ChronoUnit.DAYS.between(fromWeek, toWeek);
        int copied = 0;
        int skipped = 0;
        for (ShiftAssignment source : assignments.findInRange(fromWeek, fromWeek.plusDays(6))) {
            Employee employee = source.getEmployee();
            WorkShift shift = source.getWorkShift();
            LocalDate day = source.getWorkDate().plusDays(offset);
            if (!working(employee) || !shift.isActive() || conflictOf(employee, shift, day) != null) {
                skipped++;
                continue;
            }
            assignments.save(new ShiftAssignment(employee, shift, day, managerId));
            copied++;
        }
        return new CopyWeekResult(copied, skipped);
    }

    /** Why this person cannot take this shift on that day, or null if they can (BR-23, BR-24). */
    private String conflictOf(Employee employee, WorkShift shift, LocalDate day) {
        if (leaves.existsOverlapping(employee.getId(), day, day, List.of(LeaveStatus.APPROVED))) {
            return "Nhân viên đã được duyệt nghỉ ngày " + DAY.format(day);
        }
        for (ShiftAssignment other : assignments.findForEmployeeOn(employee.getId(), day)) {
            if (other.getWorkShift().getId().equals(shift.getId())) {
                return "Nhân viên đã được xếp ca này ngày " + DAY.format(day);
            }
            if (other.getWorkShift().overlaps(shift)) {
                return "Trùng giờ với ca " + other.getWorkShift().getName() + " ngày " + DAY.format(day);
            }
        }
        return null;
    }

    private void apply(WorkShift shift, WorkShiftRequest request) {
        if (!request.endTime().isAfter(request.startTime())) {
            throw ApiException.badRequest("Giờ kết thúc phải sau giờ bắt đầu, ca không được qua nửa đêm");
        }
        boolean timesChanged = shift.getId() != null && (!request.startTime().equals(shift.getStartTime())
                || !request.endTime().equals(shift.getEndTime()));
        if (timesChanged && assignments.existsByWorkShiftId(shift.getId())) {
            throw ApiException.conflict("Ca mẫu đã được xếp cho nhân viên nên không đổi giờ được. Hãy tạo ca mẫu mới");
        }
        shift.setName(request.name().trim());
        shift.setStartTime(request.startTime());
        shift.setEndTime(request.endTime());
        if (request.active() != null) {
            shift.setActive(request.active());
        }
    }

    static boolean working(Employee employee) {
        return employee.isActive() && employee.getLeftOn() == null;
    }
}
