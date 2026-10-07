package vn.khoibep.rms.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import vn.khoibep.rms.common.exception.ApiException;
import vn.khoibep.rms.enums.PayrollStatus;
import vn.khoibep.rms.repository.PayrollRepository;

/** BR-27: once a month's payroll is finalized, that month's attendance and leave stay as they are. */
@Component
@RequiredArgsConstructor
public class PayrollLock {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM/yyyy");

    private final PayrollRepository payrolls;

    public void requireOpen(LocalDate day) {
        requireOpen(YearMonth.from(day));
    }

    /** Every month touched by [from, to]. */
    public void requireOpen(LocalDate from, LocalDate to) {
        for (YearMonth month = YearMonth.from(from); !month.isAfter(YearMonth.from(to)); month = month.plusMonths(1)) {
            requireOpen(month);
        }
    }

    public void requireOpen(YearMonth month) {
        if (payrolls.existsByPeriodAndStatus(month.toString(), PayrollStatus.FINALIZED)) {
            throw ApiException.conflict("Bảng lương tháng " + month.format(MONTH) + " đã chốt");
        }
    }
}
