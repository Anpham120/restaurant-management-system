package vn.khoibep.rms.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.khoibep.rms.common.exception.ApiException;
import vn.khoibep.rms.common.security.CurrentUser;
import vn.khoibep.rms.common.util.DateRange;
import vn.khoibep.rms.common.util.Money;
import vn.khoibep.rms.dto.CashShiftDtos.CashShiftDto;
import vn.khoibep.rms.enums.Role;
import vn.khoibep.rms.model.CashExpense;
import vn.khoibep.rms.model.CashShift;
import vn.khoibep.rms.repository.CashExpenseRepository;
import vn.khoibep.rms.repository.CashShiftRepository;
import vn.khoibep.rms.repository.EmployeeRepository;
import vn.khoibep.rms.repository.PaymentRepository.CashTaken;
import vn.khoibep.rms.repository.PaymentRepository;

/** FR-17, BR-39: one cash drawer, opened with a float, paid out of with a reason, closed with a count. */
@Service
@RequiredArgsConstructor
public class CashShiftService {

    /** BR-39: paying out more than this needs a manager. */
    static final long EXPENSE_LIMIT = 300_000;

    /** One look covers at most about a quarter. */
    static final int MAX_DAYS = 92;

    private final CashShiftRepository shifts;
    private final CashExpenseRepository expenses;
    private final PaymentRepository payments;
    private final EmployeeRepository employees;
    private final CurrentUser currentUser;
    private final Clock clock;

    /** The open shift with the cash expected in the drawer right now, if there is one. */
    @Transactional(readOnly = true)
    public Optional<CashShiftDto> current() {
        return shifts.findOpen().map(this::summary);
    }

    /** The unique index ux_cash_shift_open turns away a second shift opened at the same moment. */
    @Transactional
    public CashShiftDto open(long openingFloat, Long employeeId) {
        if (shifts.findOpen().isPresent()) {
            throw ApiException.conflict("Đang có ca mở: chốt ca đó trước khi mở ca mới");
        }
        CashShift shift = new CashShift(employees.getReferenceById(employeeId), openingFloat, clock.instant());
        return summary(shifts.saveAndFlush(shift));
    }

    @Transactional
    public CashShiftDto addExpense(long amount, String reason, Long employeeId) {
        if (amount > EXPENSE_LIMIT) {
            currentUser.require(Role.MANAGER, "Phiếu chi trên 300.000 đ cần quản lý lập");
        }
        CashShift shift = lockOpen();
        long expected = summary(shift).expectedCash();
        if (amount > expected) {
            throw ApiException.conflict("Két không đủ tiền: tiền mặt dự kiến còn " + Money.vnd(expected));
        }
        expenses.save(new CashExpense(shift, amount, reason.trim(), employees.getReferenceById(employeeId),
                clock.instant()));
        return summary(shift);
    }

    /** BR-39: what was expected is recorded beside the count, so the shift reads the same for good. */
    @Transactional
    public CashShiftDto close(long countedCash, String note, Long employeeId) {
        CashShift shift = lockOpen();
        String why = note == null || note.isBlank() ? null : note.trim();
        shift.close(summary(shift).expectedCash(), countedCash, why, employees.getReferenceById(employeeId),
                clock.instant());
        return summary(shift);
    }

    /** Shifts opened on these Vietnam dates, newest first. */
    @Transactional(readOnly = true)
    public List<CashShiftDto> list(LocalDate from, LocalDate to) {
        DateRange.check(from, to, MAX_DAYS);
        ZoneId zone = clock.getZone();
        List<CashShift> found = shifts.findOpenedBetween(from.atStartOfDay(zone).toInstant(),
                to.plusDays(1).atStartOfDay(zone).toInstant());
        return summaries(found);
    }

    /** The open shift, locked: cash taken, paid out or counted waits, so closing cannot miss any of it. */
    public CashShift lockOpen() {
        return shifts.findOpenForUpdate()
                .orElseThrow(() -> ApiException.conflict("Chưa mở ca: mở ca ở màn hình thu ngân trước"));
    }

    private CashShiftDto summary(CashShift shift) {
        return summaries(List.of(shift)).getFirst();
    }

    private List<CashShiftDto> summaries(List<CashShift> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<Long> ids = list.stream().map(CashShift::getId).toList();
        Map<Long, CashTaken> taken = payments.cashTakenIn(ids).stream()
                .collect(Collectors.toMap(CashTaken::getShiftId, Function.identity()));
        Map<Long, List<CashExpense>> paidOut = expenses.findByShiftIds(ids).stream()
                .collect(Collectors.groupingBy(e -> e.getShift().getId()));
        return list.stream().map(s -> {
            CashTaken cash = taken.get(s.getId());
            return CashShiftDto.from(s, cash == null ? 0 : cash.getAmount(), cash == null ? 0 : cash.getPayments(),
                    paidOut.getOrDefault(s.getId(), List.of()));
        }).toList();
    }
}
