package vn.bnn.rms.leave.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.bnn.rms.attendance.repository.AttendanceRepository;
import vn.bnn.rms.common.exception.ApiException;
import vn.bnn.rms.employee.entity.Employee;
import vn.bnn.rms.employee.repository.EmployeeRepository;
import vn.bnn.rms.leave.dto.LeaveDtos.CreateLeaveRequest;
import vn.bnn.rms.leave.dto.LeaveDtos.LeaveDto;
import vn.bnn.rms.leave.entity.LeaveRequest;
import vn.bnn.rms.leave.enums.LeaveStatus;
import vn.bnn.rms.leave.repository.LeaveRequestRepository;
import vn.bnn.rms.payroll.service.PayrollLock;
import vn.bnn.rms.schedule.repository.ShiftAssignmentRepository;

/** FR-13.5, FR-13.6, BR-24: leave requests and their approval. */
@Service
@RequiredArgsConstructor
public class LeaveService {

    /** Longest single request; a longer absence is split into several requests. */
    static final int MAX_DAYS = 31;

    private final LeaveRequestRepository leaves;
    private final EmployeeRepository employees;
    private final AttendanceRepository attendance;
    private final ShiftAssignmentRepository assignments;
    private final PayrollLock payrollLock;
    private final Clock clock;

    @Transactional
    public LeaveDto create(Long employeeId, CreateLeaveRequest request) {
        LocalDate from = request.fromDate();
        LocalDate to = request.toDate();
        if (to.isBefore(from)) {
            throw ApiException.badRequest("Ngày kết thúc phải từ ngày bắt đầu trở đi");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw ApiException.badRequest("Mỗi đơn nghỉ tối đa " + MAX_DAYS + " ngày");
        }
        if (leaves.existsOverlapping(employeeId, from, to, List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED))) {
            throw ApiException.conflict("Đã có đơn nghỉ trùng những ngày này");
        }
        return toDto(leaves.save(new LeaveRequest(employees.getReferenceById(employeeId), from, to, request.type(),
                request.reason().trim())));
    }

    @Transactional
    public LeaveDto cancel(Long id, Long employeeId) {
        LeaveRequest leave = get(id);
        if (!leave.getEmployee().getId().equals(employeeId)) {
            throw ApiException.forbidden("Chỉ người gửi mới huỷ được đơn");
        }
        requirePending(leave, "huỷ");
        leave.setStatus(LeaveStatus.CANCELLED);
        return toDto(leave);
    }

    /** FR-13.6: approving drops the shifts scheduled on those days. A day with a clock-in cannot be leave. */
    @Transactional
    public LeaveDto approve(Long id, String note, Long managerId) {
        LeaveRequest leave = get(id);
        requireSomeoneElse(leave, managerId);
        requirePending(leave, "duyệt");
        payrollLock.requireOpen(leave.getFromDate(), leave.getToDate());
        Long employeeId = leave.getEmployee().getId();
        ZoneId zone = clock.getZone();
        if (attendance.existsFor(employeeId, leave.getFromDate().atStartOfDay(zone).toInstant(),
                leave.getToDate().plusDays(1).atStartOfDay(zone).toInstant())) {
            throw ApiException.conflict("Nhân viên đã chấm công trong những ngày này nên không duyệt nghỉ được");
        }
        assignments.deleteUnworked(employeeId, leave.getFromDate(), leave.getToDate());
        decide(leave, LeaveStatus.APPROVED, note, managerId);
        return toDto(leave);
    }

    @Transactional
    public LeaveDto reject(Long id, String note, Long managerId) {
        if (note == null || note.isBlank()) {
            throw ApiException.badRequest("Từ chối phải có lý do");
        }
        LeaveRequest leave = get(id);
        requireSomeoneElse(leave, managerId);
        requirePending(leave, "từ chối");
        decide(leave, LeaveStatus.REJECTED, note, managerId);
        return toDto(leave);
    }

    /** All requests, or only those in one state. */
    @Transactional(readOnly = true)
    public List<LeaveDto> list(LeaveStatus status) {
        return toDtos(status == null ? leaves.findAllWithEmployee() : leaves.findByStatusWithEmployee(status));
    }

    @Transactional(readOnly = true)
    public List<LeaveDto> mine(Long employeeId) {
        return toDtos(leaves.findForEmployee(employeeId));
    }

    private void decide(LeaveRequest leave, LeaveStatus status, String note, Long managerId) {
        leave.setStatus(status);
        leave.setDecidedBy(managerId);
        leave.setDecidedAt(clock.instant());
        leave.setDecisionNote(note == null || note.isBlank() ? null : note.trim());
    }

    private static void requirePending(LeaveRequest leave, String action) {
        if (leave.getStatus() != LeaveStatus.PENDING) {
            throw ApiException.conflict("Chỉ " + action + " được đơn đang chờ duyệt");
        }
    }

    private static void requireSomeoneElse(LeaveRequest leave, Long managerId) {
        if (leave.getEmployee().getId().equals(managerId)) {
            throw ApiException.forbidden("Không tự duyệt đơn nghỉ của chính mình");
        }
    }

    private LeaveRequest get(Long id) {
        return leaves.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn nghỉ"));
    }

    private LeaveDto toDto(LeaveRequest leave) {
        return toDtos(List.of(leave)).get(0);
    }

    private List<LeaveDto> toDtos(List<LeaveRequest> list) {
        Map<Long, String> deciders = employees.findAllById(list.stream().map(LeaveRequest::getDecidedBy)
                        .filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Employee::getId, Employee::getFullName));
        return list.stream().map(l -> new LeaveDto(l.getId(), l.getEmployee().getId(), l.getEmployee().getFullName(),
                l.getFromDate(), l.getToDate(), (int) ChronoUnit.DAYS.between(l.getFromDate(), l.getToDate()) + 1,
                l.getType(), l.getReason(), l.getStatus(), deciders.get(l.getDecidedBy()), l.getDecisionNote(),
                l.getCreatedAt(), l.getDecidedAt())).toList();
    }
}
