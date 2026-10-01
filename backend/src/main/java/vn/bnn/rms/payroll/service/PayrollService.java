package vn.bnn.rms.payroll.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.bnn.rms.attendance.entity.Attendance;
import vn.bnn.rms.attendance.repository.AttendanceRepository;
import vn.bnn.rms.common.exception.ApiException;
import vn.bnn.rms.employee.entity.Employee;
import vn.bnn.rms.employee.repository.EmployeeRepository;
import vn.bnn.rms.leave.entity.LeaveRequest;
import vn.bnn.rms.leave.enums.LeaveStatus;
import vn.bnn.rms.leave.enums.LeaveType;
import vn.bnn.rms.leave.repository.LeaveRequestRepository;
import vn.bnn.rms.payroll.dto.PayrollDtos.AdjustmentDto;
import vn.bnn.rms.payroll.dto.PayrollDtos.AdjustmentRequest;
import vn.bnn.rms.payroll.dto.PayrollDtos.CreatePayrollRequest;
import vn.bnn.rms.payroll.dto.PayrollDtos.MyPayslipDto;
import vn.bnn.rms.payroll.dto.PayrollDtos.PayrollDetailDto;
import vn.bnn.rms.payroll.dto.PayrollDtos.PayrollSummaryDto;
import vn.bnn.rms.payroll.dto.PayrollDtos.PayslipDto;
import vn.bnn.rms.payroll.entity.PayAdjustment;
import vn.bnn.rms.payroll.entity.Payroll;
import vn.bnn.rms.payroll.entity.Payslip;
import vn.bnn.rms.payroll.enums.PayrollStatus;
import vn.bnn.rms.payroll.repository.PayAdjustmentRepository;
import vn.bnn.rms.payroll.repository.PayrollRepository;
import vn.bnn.rms.payroll.repository.PayslipRepository;

/** FR-15, BR-26, BR-27: monthly payroll from attendance and paid leave. */
@Service
@RequiredArgsConstructor
public class PayrollService {

    static final int DEFAULT_STANDARD_DAYS = 26;

    private final PayrollRepository payrolls;
    private final PayslipRepository payslips;
    private final PayAdjustmentRepository adjustments;
    private final AttendanceRepository attendance;
    private final LeaveRequestRepository leaves;
    private final EmployeeRepository employees;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<PayrollSummaryDto> list() {
        Map<Long, Object[]> totals = payslips.totalsByPayroll().stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> row));
        return payrolls.findAllByOrderByPeriodDesc().stream().map(p -> {
            Object[] row = totals.get(p.getId());
            return new PayrollSummaryDto(p.getId(), p.getPeriod(), p.getStatus(), p.getStandardDays(),
                    row == null ? 0 : ((Number) row[1]).longValue(), row == null ? 0 : ((Number) row[2]).longValue(),
                    p.getFinalizedAt());
        }).toList();
    }

    /** FR-15.1: a draft for a month that has started, calculated at once. */
    @Transactional
    public PayrollDetailDto create(CreatePayrollRequest request, Long adminId) {
        YearMonth month = YearMonth.parse(request.period());
        if (month.isAfter(YearMonth.now(clock))) {
            throw ApiException.badRequest("Tháng này chưa bắt đầu");
        }
        if (payrolls.existsByPeriod(request.period())) {
            throw ApiException.conflict("Tháng này đã có bảng lương");
        }
        int standardDays = request.standardDays() == null ? DEFAULT_STANDARD_DAYS : request.standardDays();
        Payroll payroll = payrolls.save(new Payroll(month, standardDays, adminId));
        calculate(payroll);
        return detail(payroll);
    }

    @Transactional(readOnly = true)
    public PayrollDetailDto get(Long id) {
        return detail(find(id));
    }

    /** FR-15.1: takes in attendance, leave and pay changes while the payroll is a draft. */
    @Transactional
    public PayrollDetailDto recalculate(Long id, Integer standardDays) {
        Payroll payroll = find(id);
        requireDraft(payroll);
        if (standardDays != null) {
            payroll.setStandardDays(standardDays);
        }
        calculate(payroll);
        return detail(payroll);
    }

    /** FR-15.3, BR-27: only once the month is over and nobody is still clocked in. */
    @Transactional
    public PayrollDetailDto finalizePayroll(Long id, Long adminId) {
        Payroll payroll = find(id);
        requireDraft(payroll);
        YearMonth month = payroll.month();
        if (!month.isBefore(YearMonth.now(clock))) {
            throw ApiException.conflict("Chỉ chốt được khi tháng đã kết thúc");
        }
        if (attendance.existsOpenBetween(startOf(month), startOf(month.plusMonths(1)))) {
            throw ApiException.conflict("Còn người chưa ra ca trong tháng. Hãy sửa chấm công trước khi chốt");
        }
        calculate(payroll);
        payroll.setStatus(PayrollStatus.FINALIZED);
        payroll.setFinalizedBy(adminId);
        payroll.setFinalizedAt(clock.instant());
        return detail(payroll);
    }

    /** FR-15.2: net pay never goes below zero (BR-26). */
    @Transactional
    public PayslipDto addAdjustment(Long payslipId, AdjustmentRequest request, Long adminId) {
        if (request.amount() == 0) {
            throw ApiException.badRequest("Số tiền thưởng hoặc phạt phải khác 0");
        }
        Payslip slip = payslips.findById(payslipId).orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu lương"));
        requireDraft(slip.getPayroll());
        long total = slip.getAdjustmentAmount() + request.amount();
        requireNotNegative(slip.getBaseAmount() + total);
        adjustments.save(new PayAdjustment(slip, request.amount(), request.reason().trim(), adminId));
        slip.setAmounts(slip.getBaseAmount(), total);
        return payslipDto(slip);
    }

    @Transactional
    public PayslipDto removeAdjustment(Long adjustmentId) {
        PayAdjustment adjustment = adjustments.findById(adjustmentId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy khoản thưởng, phạt"));
        Payslip slip = adjustment.getPayslip();
        requireDraft(slip.getPayroll());
        long total = slip.getAdjustmentAmount() - adjustment.getAmount();
        requireNotNegative(slip.getBaseAmount() + total);
        adjustments.delete(adjustment);
        slip.setAmounts(slip.getBaseAmount(), total);
        return payslipDto(slip);
    }

    /** FR-15.4: one's own payslips, once finalized. */
    @Transactional(readOnly = true)
    public List<MyPayslipDto> mine(Long employeeId) {
        return payslips.findForEmployee(employeeId, PayrollStatus.FINALIZED).stream()
                .map(p -> new MyPayslipDto(p.getId(), p.getPayroll().getPeriod(), p.getNetAmount()))
                .toList();
    }

    /** FR-15.4, BR-27: nobody sees another person's payslip here, nor a draft. */
    @Transactional(readOnly = true)
    public PayslipDto myPayslip(Long payslipId, Long employeeId) {
        Payslip slip = payslips.findById(payslipId).orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu lương"));
        if (!slip.getEmployee().getId().equals(employeeId) || slip.getPayroll().getStatus() != PayrollStatus.FINALIZED) {
            throw ApiException.forbidden("Bạn chỉ xem được phiếu lương đã chốt của chính mình");
        }
        return payslipDto(slip);
    }

    /**
     * BR-26: a payslip for everyone with a closed clock-in or paid leave in the month. Pay type and rate are
     * copied from the profile; bonuses and deductions already entered are kept.
     */
    private void calculate(Payroll payroll) {
        YearMonth month = payroll.month();
        ZoneId zone = clock.getZone();
        LocalDate first = month.atDay(1);
        LocalDate last = month.atEndOfMonth();

        Map<Long, List<Attendance>> worked = attendance.findClosedBetween(startOf(month), startOf(month.plusMonths(1)))
                .stream().collect(Collectors.groupingBy(t -> t.getEmployee().getId()));
        Map<Long, Integer> paidLeave = new HashMap<>();
        for (LeaveRequest leave : leaves.findOverlapping(LeaveStatus.APPROVED, first, last)) {
            if (leave.getType() != LeaveType.PAID) {
                continue;
            }
            LocalDate from = leave.getFromDate().isBefore(first) ? first : leave.getFromDate();
            LocalDate to = leave.getToDate().isAfter(last) ? last : leave.getToDate();
            paidLeave.merge(leave.getEmployee().getId(), (int) ChronoUnit.DAYS.between(from, to) + 1, Integer::sum);
        }
        Map<Long, Payslip> existing = payslips.findForPayroll(payroll.getId()).stream()
                .collect(Collectors.toMap(p -> p.getEmployee().getId(), p -> p));

        // People already on the payroll stay on it, e.g. if their attendance was moved to another month.
        Set<Long> people = new HashSet<>(worked.keySet());
        people.addAll(paidLeave.keySet());
        people.addAll(existing.keySet());
        for (Employee employee : employees.findAllById(people)) {
            List<Attendance> records = worked.getOrDefault(employee.getId(), List.of());
            int minutes = records.stream().mapToInt(Attendance::getWorkedMinutes).sum();
            int days = (int) records.stream().map(t -> LocalDate.ofInstant(t.getCheckInAt(), zone)).distinct().count();
            int leaveDays = paidLeave.getOrDefault(employee.getId(), 0);
            long base = PayCalculator.base(employee.getPayType(), employee.getPayRate(), minutes, days, leaveDays,
                    payroll.getStandardDays());

            Payslip slip = existing.get(employee.getId());
            if (slip == null) {
                slip = new Payslip(payroll, employee);
            }
            long adjustmentTotal = slip.getId() == null ? 0 : adjustments.sumForPayslip(slip.getId());
            if (base + adjustmentTotal < 0) {
                throw ApiException.conflict("Thực nhận của " + employee.getFullName()
                        + " sẽ bị âm. Hãy bớt khoản phạt trước khi tính lại");
            }
            slip.setPayType(employee.getPayType());
            slip.setPayRate(employee.getPayRate());
            slip.setWorkedMinutes(minutes);
            slip.setWorkDays(days);
            slip.setPaidLeaveDays(leaveDays);
            slip.setAmounts(base, adjustmentTotal);
            payslips.save(slip);
        }
    }

    private PayrollDetailDto detail(Payroll payroll) {
        List<Payslip> list = payslips.findForPayroll(payroll.getId());
        Map<Long, List<AdjustmentDto>> byPayslip = adjustmentsOf(list);
        String finalizedBy = payroll.getFinalizedBy() == null ? null
                : employees.findById(payroll.getFinalizedBy()).map(Employee::getFullName).orElse(null);
        return new PayrollDetailDto(payroll.getId(), payroll.getPeriod(), payroll.getStatus(),
                payroll.getStandardDays(), list.size(), list.stream().mapToLong(Payslip::getNetAmount).sum(),
                payroll.getFinalizedAt(), finalizedBy,
                list.stream().map(p -> payslipDto(p, byPayslip.getOrDefault(p.getId(), List.of()))).toList());
    }

    private PayslipDto payslipDto(Payslip slip) {
        return payslipDto(slip, adjustmentsOf(List.of(slip)).getOrDefault(slip.getId(), List.of()));
    }

    private static PayslipDto payslipDto(Payslip p, List<AdjustmentDto> adjustmentList) {
        Employee e = p.getEmployee();
        return new PayslipDto(p.getId(), p.getPayroll().getPeriod(), p.getPayroll().getStatus(), e.getId(),
                e.getFullName(), e.getRole(), p.getPayType(), p.getPayRate(), p.getWorkedMinutes(), p.getWorkDays(),
                p.getPaidLeaveDays(), p.getBaseAmount(), p.getAdjustmentAmount(), p.getNetAmount(), adjustmentList);
    }

    private Map<Long, List<AdjustmentDto>> adjustmentsOf(List<Payslip> list) {
        if (list.isEmpty()) {
            return Map.of();
        }
        return adjustments.findByPayslipIdInOrderByCreatedAtAscIdAsc(list.stream().map(Payslip::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(a -> a.getPayslip().getId(), Collectors.mapping(
                        a -> new AdjustmentDto(a.getId(), a.getAmount(), a.getReason(), a.getCreatedAt()),
                        Collectors.toList())));
    }

    private static void requireDraft(Payroll payroll) {
        if (payroll.getStatus() != PayrollStatus.DRAFT) {
            throw ApiException.conflict("Bảng lương đã chốt, không sửa được nữa");
        }
    }

    private static void requireNotNegative(long net) {
        if (net < 0) {
            throw ApiException.conflict("Thực nhận không được âm: tổng phạt đang lớn hơn lương theo công");
        }
    }

    private Payroll find(Long id) {
        return payrolls.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy bảng lương"));
    }

    private Instant startOf(YearMonth month) {
        return month.atDay(1).atStartOfDay(clock.getZone()).toInstant();
    }
}
