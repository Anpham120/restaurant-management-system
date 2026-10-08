package vn.khoibep.rms.dto;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import vn.khoibep.rms.enums.PayType;
import vn.khoibep.rms.enums.PayrollStatus;
import vn.khoibep.rms.enums.Role;

public final class PayrollDtos {

    private PayrollDtos() {
    }

    public record PayrollSummaryDto(Long id, String period, PayrollStatus status, int standardDays, long payslipCount,
                                    long totalNet, Instant finalizedAt) {
    }

    public record PayrollDetailDto(Long id, String period, PayrollStatus status, int standardDays, long payslipCount,
                                   long totalNet, Instant finalizedAt, String finalizedByName,
                                   List<PayslipDto> payslips) {
    }

    public record PayslipDto(Long id, String period, PayrollStatus status, Long employeeId, String employeeName,
                             Role role, PayType payType, long payRate, int workedMinutes, int workDays,
                             int paidLeaveDays, long baseAmount, long adjustmentAmount, long netAmount,
                             List<AdjustmentDto> adjustments) {
    }

    public record AdjustmentDto(Long id, long amount, String reason, Instant createdAt) {
    }

    public record MyPayslipDto(Long id, String period, long netAmount) {
    }

    /** standardDays defaults to 26. */
    public record CreatePayrollRequest(
            @NotNull @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "phải có dạng YYYY-MM") String period,
            @Min(1) @Max(31) Integer standardDays) {
    }

    /** Leaving standardDays out keeps the current value. */
    public record RecalculateRequest(@Min(1) @Max(31) Integer standardDays) {
    }

    /** A positive amount is a bonus, a negative one a deduction. */
    public record AdjustmentRequest(@NotNull Long amount, @NotBlank @Size(max = 300) String reason) {
    }
}
